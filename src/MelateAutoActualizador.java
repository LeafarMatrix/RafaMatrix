import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MelateAutoActualizador
 * -----------------------------------------------------------------------
 * Descarga los históricos OFICIALES publicados por Lotería Nacional (Melate
 * y Revancha) y agrega a cada archivo histórico local únicamente los
 * sorteos con número de concurso mayor al último que ya tienes registrado
 * (evita duplicados y evita tener que comparar combinaciones número por
 * número).
 *
 * Cada producto mantiene su PROPIO archivo de marcador (ultimo_concurso.txt
 * para Melate, ultimo_concurso_revancha.txt para Revancha), porque aunque
 * comparten número de concurso, se procesan y guardan por separado: si uno
 * fallara (por ejemplo la descarga de Revancha), no debe afectar el estado
 * de Melate ni viceversa.
 *
 * IMPORTANTE sobre el formato: Melate trae número adicional (11 columnas,
 * NPRODUCTO,CONCURSO,R1..R6,R7,BOLSA,FECHA); Revancha NO tiene número
 * adicional (10 columnas, NPRODUCTO,CONCURSO,F1..F6,BOLSA,FECHA), porque su
 * urna solo sortea 6 esferas. Por eso cada ProductoConfig trae su propio
 * mínimo de columnas esperado.
 *
 * PENSADO PARA EJECUTARSE SOLO, vía tarea programada (cron / Task
 * Scheduler), después de cada sorteo (miércoles, viernes y domingo,
 * ~21:15 hrs + margen). Ver instrucciones de programación al final
 * de este archivo.
 *
 * USO MANUAL:
 *   javac --release 17 -encoding UTF-8 MelateAutoActualizador.java
 *   java MelateAutoActualizador
 * -----------------------------------------------------------------------
 */
public class MelateAutoActualizador {

    /** Config de un producto: URL oficial, archivo histórico local, marcador y columnas mínimas válidas. */
    record ProductoConfig(String nombre, String urlCsv, String archivoHistorico,
                           String archivoMarcador, int minColumnas) {}

    static final ProductoConfig MELATE = new ProductoConfig(
            "MELATE",
            "https://www.loterianacional.gob.mx/Documentos/Historicos/Melate.csv",
            "historico_melate.txt",
            "ultimo_concurso.txt",
            11); // NPRODUCTO,CONCURSO,R1..R6,R7,BOLSA,FECHA

    static final ProductoConfig REVANCHA = new ProductoConfig(
            "REVANCHA",
            "https://www.loterianacional.gob.mx/Documentos/Historicos/Revancha.csv",
            "historico_revancha.txt",
            "ultimo_concurso_revancha.txt",
            10); // NPRODUCTO,CONCURSO,F1..F6,BOLSA,FECHA (sin adicional)

    static final String ARCHIVO_LOG = "actualizacion_melate.log";

    public static void main(String[] args) {
        ejecutarTodos();
    }

    /** Actualiza Melate y Revancha en una sola corrida; un fallo en uno no detiene al otro. */
    static void ejecutarTodos() {
        try {
            ejecutar(MELATE);
        } catch (Exception e) {
            log("ERROR actualizando " + MELATE.nombre() + ": " + e.getMessage());
        }
        try {
            ejecutar(REVANCHA);
        } catch (Exception e) {
            log("ERROR actualizando " + REVANCHA.nombre() + ": " + e.getMessage());
        }
    }

    /** Compatibilidad con el codigo/tarea programada existente: solo actualiza Melate. */
    static void ejecutar() throws Exception {
        ejecutar(MELATE);
    }

    static void ejecutar(ProductoConfig cfg) throws Exception {
        int ultimoConcurso = leerUltimoConcurso(cfg);
        log("[" + cfg.nombre() + "] Último concurso registrado localmente: " + ultimoConcurso);

        String csv = descargarCSV(cfg.urlCsv());
        List<String[]> filas = parsearCSV(csv);
        log("[" + cfg.nombre() + "] Filas leídas del CSV oficial: " + filas.size());

        List<String> nuevasLineas = new ArrayList<>();
        int maxConcursoEncontrado = ultimoConcurso;

        for (String[] campos : filas) {
            if (campos.length < cfg.minColumnas()) {
				continue;
			}
            int concurso;
            try {
                concurso = Integer.parseInt(campos[1].trim());
            } catch (NumberFormatException e) {
                continue; // encabezado u otra fila no numérica
            }
            if (concurso <= ultimoConcurso) {
				continue;
			}

            // Se conserva la fila completa tal como viene del CSV oficial, para que el
            // formato coincida con el resto del historico y con lo que espera
            // HistorialParser (detecta 10 vs 11 columnas automaticamente).
            StringBuilder linea = new StringBuilder();
            for (int i = 0; i < campos.length; i++) {
                if (i > 0) {
					linea.append(",");
				}
                linea.append(campos[i].trim());
            }
            nuevasLineas.add(linea.toString());
            if (concurso > maxConcursoEncontrado) {
				maxConcursoEncontrado = concurso;
			}
        }

        if (nuevasLineas.isEmpty()) {
            log("[" + cfg.nombre() + "] No hay sorteos nuevos. Todo al día.");
            return;
        }

        boolean archivoNuevo = !Files.exists(Paths.get(cfg.archivoHistorico()));
        try (BufferedWriter bw = Files.newBufferedWriter(
                Paths.get(cfg.archivoHistorico()),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (archivoNuevo) {
                bw.write(encabezadoPara(cfg));
                bw.newLine();
            }
            for (String linea : nuevasLineas) {
                bw.write(linea);
                bw.newLine();
            }
        }

        guardarUltimoConcurso(cfg, maxConcursoEncontrado);
        log("[" + cfg.nombre() + "] Se agregaron " + nuevasLineas.size() + " sorteo(s) nuevo(s). " +
                "Último concurso ahora: " + maxConcursoEncontrado);
    }

    private static String encabezadoPara(ProductoConfig cfg) {
        return cfg.minColumnas() >= 11
                ? "NPRODUCTO,CONCURSO,R1,R2,R3,R4,R5,R6,R7,BOLSA,FECHA"
                : "NPRODUCTO,CONCURSO,F1,F2,F3,F4,F5,F6,BOLSA,FECHA";
    }

    static String descargarCSV(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("No se pudo descargar el CSV (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }

    static List<String[]> parsearCSV(String csv) {
        List<String[]> filas = new ArrayList<>();
        for (String linea : csv.split("\\R")) {
            linea = linea.trim();
            if (linea.isEmpty() || linea.toUpperCase().startsWith("NPRODUCTO")) {
				continue;
			}
            filas.add(linea.split(","));
        }
        return filas;
    }

    static int leerUltimoConcurso(ProductoConfig cfg) throws IOException {
        Path path = Paths.get(cfg.archivoMarcador());
        if (!Files.exists(path)) {
			return 0;
		}
        String contenido = Files.readString(path).trim();
        if (contenido.isEmpty()) {
			return 0;
		}
        try {
            return Integer.parseInt(contenido);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void guardarUltimoConcurso(ProductoConfig cfg, int concurso) throws IOException {
        Files.writeString(Paths.get(cfg.archivoMarcador()), String.valueOf(concurso));
    }

    static void log(String mensaje) {
        String linea = "[" + LocalDateTime.now() + "] " + mensaje;
        System.out.println(linea);
        try (BufferedWriter bw = Files.newBufferedWriter(
                Paths.get(ARCHIVO_LOG),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            bw.write(linea);
            bw.newLine();
        } catch (IOException ignored) {
        }
    }
}

/*
 * -----------------------------------------------------------------------
 * PROGRAMAR EJECUCIÓN AUTOMÁTICA
 * -----------------------------------------------------------------------
 *
 * Primero compílalo una vez:
 *   javac --release 17 -encoding UTF-8 MelateAutoActualizador.java
 *
 * Los sorteos son miércoles, viernes y domingo a las 21:15. Programa la
 * tarea para que corra con margen, por ejemplo a las 23:00 esos días.
 * Esta corrida ahora actualiza Melate Y Revancha en el mismo paso.
 *
 * WINDOWS (Task Scheduler / schtasks):
 *   schtasks /Create /SC WEEKLY /D WED,FRI,SUN /ST 23:00 ^
 *     /TN "MelateAutoActualizador" ^
 *     /TR "java -cp C:\ruta\a\tu\carpeta MelateAutoActualizador"
 *
 * LINUX/macOS (cron) - edita con `crontab -e` y agrega:
 *   0 23 * * 3,5,0 cd /ruta/a/tu/carpeta && java MelateAutoActualizador >> cron.log 2>&1
 *   (3=miércoles, 5=viernes, 0=domingo)
 *
 * Revisa "actualizacion_melate.log" después de cada corrida para
 * confirmar que se agregaron los sorteos nuevos de ambos productos.
 * -----------------------------------------------------------------------
 */
