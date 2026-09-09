import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HistoricoRepository
 * -----------------------------------------------------------------------
 * Unica clase responsable de tocar los archivos de historico y de
 * comunicarse con el sitio de Loteria Nacional. El resto del programa
 * nunca lee/escribe estos archivos directamente.
 *
 * (Version compatible con Java 8: usa HttpURLConnection en vez de
 * java.net.http.HttpClient, que requiere Java 11+)
 * -----------------------------------------------------------------------
 */
public class HistoricoRepository {

    /** Resultado de una actualizacion (version Java 8: clase normal en vez de record). */
    public static class ResultadoActualizacion {
        private final int sorteosAgregados;
        private final int ultimoConcurso;
        private final boolean yaEstabaAlDia;

        public ResultadoActualizacion(int sorteosAgregados, int ultimoConcurso, boolean yaEstabaAlDia) {
            this.sorteosAgregados = sorteosAgregados;
            this.ultimoConcurso = ultimoConcurso;
            this.yaEstabaAlDia = yaEstabaAlDia;
        }

        public int sorteosAgregados() { return sorteosAgregados; }
        public int ultimoConcurso() { return ultimoConcurso; }
        public boolean yaEstabaAlDia() { return yaEstabaAlDia; }
    }

    /** Descarga el CSV oficial y agrega al historico local solo los sorteos nuevos. */
    public ResultadoActualizacion actualizar(ProductoConfig producto) throws Exception {
        int ultimoConcurso = leerUltimoConcurso(producto);
        String csv = descargarCSV(producto.urlCsv());
        List<String[]> filas = parsearCSV(csv);

        List<String> nuevasLineas = new ArrayList<String>();
        int maxConcursoEncontrado = ultimoConcurso;

        for (String[] campos : filas) {
            if (campos.length < producto.columnasCsv()) continue;
            int concurso;
            try {
                concurso = Integer.parseInt(campos[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (concurso <= ultimoConcurso) continue;

            StringBuilder linea = new StringBuilder();
            for (int i = 0; i < campos.length; i++) {
                if (i > 0) linea.append(",");
                linea.append(campos[i].trim());
            }
            nuevasLineas.add(linea.toString());
            if (concurso > maxConcursoEncontrado) maxConcursoEncontrado = concurso;
        }

        if (nuevasLineas.isEmpty()) {
            return new ResultadoActualizacion(0, ultimoConcurso, true);
        }

        java.util.Collections.sort(nuevasLineas, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int concursoA = Integer.parseInt(a.split(",")[1].trim());
                int concursoB = Integer.parseInt(b.split(",")[1].trim());
                return concursoB - concursoA; // mas reciente primero
            }
        });

        insertarAlInicio(producto, nuevasLineas);
        guardarUltimoConcurso(producto, maxConcursoEncontrado);

        return new ResultadoActualizacion(nuevasLineas.size(), maxConcursoEncontrado, false);
    }

    /** Lee el historico local y devuelve los sorteos (solo R1..R6) del mas reciente al mas antiguo. */
    public List<int[]> cargarNumeros(ProductoConfig producto) throws IOException {
        List<int[]> sorteos = new ArrayList<int[]>();
        Path path = Paths.get(producto.archivoHistorico());
        if (!Files.exists(path)) return sorteos;

        for (String linea : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;
            if (linea.toUpperCase().startsWith("NPRODUCTO")) continue;

            String[] campos = linea.split(",");
            int[] combinacion = new int[producto.numerosPorSorteo()];
            try {
                if (campos.length >= producto.columnasCsv()) {
                    for (int i = 0; i < producto.numerosPorSorteo(); i++) {
                        combinacion[i] = Integer.parseInt(campos[2 + i].trim());
                    }
                } else if (campos.length == producto.numerosPorSorteo()) {
                    for (int i = 0; i < producto.numerosPorSorteo(); i++) {
                        combinacion[i] = Integer.parseInt(campos[i].trim());
                    }
                } else {
                    continue;
                }
            } catch (NumberFormatException e) {
                continue;
            }

            boolean valido = true;
            for (int n : combinacion) {
                if (n < 1 || n > producto.numeroMaximo()) valido = false;
            }
            if (valido) sorteos.add(combinacion);
        }
        return sorteos;
    }

    // ---------------------- privados ----------------------

    private void insertarAlInicio(ProductoConfig producto, List<String> nuevasLineas) throws IOException {
        Path path = Paths.get(producto.archivoHistorico());
        List<String> existentes = Files.exists(path)
                ? Files.readAllLines(path, StandardCharsets.UTF_8)
                : new ArrayList<String>();

        List<String> resultado = new ArrayList<String>();
        int idxInsercion = 0;
        if (!existentes.isEmpty() && existentes.get(0).toUpperCase().startsWith("NPRODUCTO")) {
            resultado.add(existentes.get(0)); // conserva encabezado real ya existente
            idxInsercion = 1;
        } else {
            resultado.add(producto.encabezadoOficial()); // archivo nuevo: encabezado correcto
        }
        resultado.addAll(nuevasLineas);
        for (int i = idxInsercion; i < existentes.size(); i++) {
            if (!existentes.get(i).trim().isEmpty()) resultado.add(existentes.get(i));
        }

        Files.write(path, resultado, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Descarga usando HttpURLConnection (disponible desde Java 1.1, sin dependencias nuevas). */
    private String descargarCSV(String urlTexto) throws IOException {
        URL url = new URL(urlTexto);
        HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
        conexion.setRequestMethod("GET");
        conexion.setRequestProperty("User-Agent", "Mozilla/5.0");
        conexion.setConnectTimeout(15000);
        conexion.setReadTimeout(30000);

        int status = conexion.getResponseCode();
        if (status != 200) {
            throw new IOException("No se pudo descargar el CSV (HTTP " + status + ") de " + urlTexto);
        }

        StringBuilder contenido = new StringBuilder();
        InputStream in = conexion.getInputStream();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String linea;
            while ((linea = br.readLine()) != null) {
                contenido.append(linea).append("\n");
            }
        } finally {
            in.close();
        }
        return contenido.toString();
    }

    private List<String[]> parsearCSV(String csv) {
        List<String[]> filas = new ArrayList<String[]>();
        for (String linea : csv.split("\\r?\\n")) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;
            if (linea.toUpperCase().startsWith("NPRODUCTO")) continue;
            filas.add(linea.split(","));
        }
        return filas;
    }

    private int leerUltimoConcurso(ProductoConfig producto) throws IOException {
        Path path = Paths.get(producto.archivoMarcador());
        if (!Files.exists(path)) return 0;
        List<String> lineas = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lineas.isEmpty()) return 0;
        String contenido = lineas.get(0).trim();
        if (contenido.isEmpty()) return 0;
        try {
            return Integer.parseInt(contenido);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void guardarUltimoConcurso(ProductoConfig producto, int concurso) throws IOException {
        Files.write(Paths.get(producto.archivoMarcador()),
                Arrays.asList(String.valueOf(concurso)), StandardCharsets.UTF_8);
    }
}
