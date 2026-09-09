import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main
 * -----------------------------------------------------------------------
 * RafaMatrix - Sistema de analisis estadistico para Melate y Revancha
 *
 * USO:
 *   java Main --producto MELATE   --accion todo
 *   java Main --producto REVANCHA --accion actualizar
 *   java Main --producto MELATE   --accion analizar
 *   java Main --producto MELATE   --accion jugadas
 *   java Main --producto MELATE   --accion backtest --ventana 1000
 *
 * Parametros:
 *   --producto   MELATE | REVANCHA               (default: MELATE)
 *   --accion     todo | actualizar | analizar | jugadas | backtest
 *                (default: todo = actualizar + analizar + jugadas)
 *   --ventana    N sorteos recientes para el backtest (default: 500)
 *   --cantidad   cuantas jugadas generar (default: 4)
 *
 * (Version compatible con Java 8)
 * -----------------------------------------------------------------------
 */
public class Main {

    public static void main(String[] args) {
        Map<String, String> opciones = parsearArgs(args);

        ProductoConfig producto = Productos.porNombre(getOrDefault(opciones, "producto", "MELATE"));
        String accion = getOrDefault(opciones, "accion", "todo").toLowerCase();
        int ventana = Integer.parseInt(getOrDefault(opciones, "ventana", "500"));
        int cantidad = Integer.parseInt(getOrDefault(opciones, "cantidad", "4"));

        HistoricoRepository repo = new HistoricoRepository();

        try {
            if (accion.equals("todo") || accion.equals("actualizar")) {
                actualizar(repo, producto);
            }
            if (accion.equals("todo") || accion.equals("analizar")) {
                analizar(repo, producto);
            }
            if (accion.equals("todo") || accion.equals("jugadas")) {
                generarJugadas(repo, producto, cantidad);
            }
            if (accion.equals("backtest")) {
                backtest(repo, producto, ventana);
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void actualizar(HistoricoRepository repo, ProductoConfig producto) throws Exception {
        titulo("PASO 1: Actualizando historico de " + producto.nombre());
        HistoricoRepository.ResultadoActualizacion resultado = repo.actualizar(producto);
        if (resultado.yaEstabaAlDia()) {
            System.out.println("No hay sorteos nuevos. Al dia (concurso " + resultado.ultimoConcurso() + ").");
        } else {
            System.out.println("Se agregaron " + resultado.sorteosAgregados() +
                    " sorteo(s) nuevo(s). Ultimo concurso ahora: " + resultado.ultimoConcurso());
        }
        System.out.println();
    }

    static void analizar(HistoricoRepository repo, ProductoConfig producto) throws Exception {
        titulo("PASO 2: Analizando historico de " + producto.nombre());
        List<int[]> sorteos = repo.cargarNumeros(producto);
        System.out.println("Sorteos analizados: " + sorteos.size());
        if (sorteos.isEmpty()) {
            System.out.println("No hay datos para analizar todavia.");
            return;
        }

        AnalizadorEstadistico analizador = new AnalizadorEstadistico(producto);
        int[] frecuencia = analizador.frecuencia(sorteos);
        Map<String, Integer> pares = analizador.pares(sorteos);
        Map<String, Integer> trios = analizador.trios(sorteos);

        List<Integer> ordenados = analizador.ordenadosPorFrecuencia(frecuencia);
        System.out.println("\nTop 10 numeros MAS frecuentes: " + ordenados.subList(0, 10));
        System.out.println("Top 10 numeros MENOS frecuentes: " + ordenados.subList(ordenados.size() - 10, ordenados.size()));

        mostrarTop("\nTop 5 pares mas frecuentes:", pares, 5);
        mostrarTop("\nTop 5 trios mas frecuentes:", trios, 5);

        System.out.println();
    }

    static void generarJugadas(HistoricoRepository repo, ProductoConfig producto, int cantidad) throws Exception {
        titulo("PASO 3: Generando jugadas para " + producto.nombre());
        List<int[]> sorteos = repo.cargarNumeros(producto);
        if (sorteos.isEmpty()) {
            System.out.println("No hay historico suficiente para generar jugadas.");
            return;
        }

        AnalizadorEstadistico analizador = new AnalizadorEstadistico(producto);
        int[] frecuencia = analizador.frecuencia(sorteos);
        GeneradorJugadas generador = new GeneradorJugadas(producto);

        System.out.println("Filtro suma: " + producto.sumaFiltroMin() + "-" + producto.sumaFiltroMax() +
                " | Diversidad minima: 3 decenas distintas\n");

        imprimirJugadas("CALIENTE", generador.generarVarias(GeneradorJugadas.Estrategia.CALIENTE, frecuencia, cantidad));
        imprimirJugadas("FRIO", generador.generarVarias(GeneradorJugadas.Estrategia.FRIO, frecuencia, cantidad));
        imprimirJugadas("BALANCEADA", generador.generarVarias(GeneradorJugadas.Estrategia.BALANCEADA, frecuencia, cantidad));
        imprimirJugadas("ALEATORIA (control)", generador.generarVarias(GeneradorJugadas.Estrategia.ALEATORIA, frecuencia, cantidad));

        System.out.println("\nRecordatorio: cada sorteo es independiente y aleatorio (confirmado con");
        System.out.println("el backtest). Estas jugadas estan bien estructuradas, no son predicciones.");
        System.out.println();
    }

    static void backtest(HistoricoRepository repo, ProductoConfig producto, int ventana) throws Exception {
        titulo("BACKTEST: " + producto.nombre() + " (ventana = " + ventana + " sorteos)");
        List<int[]> sorteos = repo.cargarNumeros(producto);
        if (sorteos.size() < 50) {
            System.out.println("Se necesitan al menos 50 sorteos para un backtest util. Tienes: " + sorteos.size());
            return;
        }

        BacktestService servicio = new BacktestService(producto);
        BacktestService.Resultado r = servicio.correr(sorteos, ventana);

        System.out.println("Sorteos evaluados: " + r.sorteosEvaluados());
        System.out.printf("Promedio de aciertos - PONDERADO: %.4f%n", r.promedioPonderado());
        System.out.printf("Promedio de aciertos - ALEATORIO: %.4f%n", r.promedioAleatorio());
        System.out.printf("Promedio TEORICO esperado:        %.4f%n", r.promedioTeorico());
        System.out.printf("Diferencia (ponderado - aleatorio): %+.4f%n", r.diferencia());
        System.out.println();
        if (Math.abs(r.diferencia()) < 0.05) {
            System.out.println("=> Diferencia minima: confirma que el sorteo es independiente y aleatorio.");
        } else {
            System.out.println("=> Diferencia mayor a la esperada por ruido; revisar tamano de muestra.");
        }
        System.out.println();
    }

    // ---------------------- utilidades de salida ----------------------

    static void imprimirJugadas(String etiqueta, List<int[]> jugadas) {
        System.out.println(etiqueta + ":");
        char letra = 'A';
        for (int[] j : jugadas) {
            int suma = 0;
            for (int n : j) suma += n;
            System.out.println("  Jugada " + letra + ": " + Arrays.toString(j) + " (suma=" + suma + ")");
            letra++;
        }
        System.out.println();
    }

    static void mostrarTop(String encabezado, Map<String, Integer> conteo, int top) {
        System.out.println(encabezado);
        List<Map.Entry<String, Integer>> lista = new ArrayList<Map.Entry<String, Integer>>(conteo.entrySet());
        java.util.Collections.sort(lista, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return b.getValue() - a.getValue();
            }
        });
        for (int i = 0; i < top && i < lista.size(); i++) {
            Map.Entry<String, Integer> e = lista.get(i);
            System.out.println("  [" + e.getKey() + "] -> " + e.getValue() + " veces");
        }
    }

    static void titulo(String texto) {
        System.out.println("=========================================================");
        System.out.println(" " + texto);
        System.out.println("=========================================================");
    }

    static Map<String, String> parsearArgs(String[] args) {
        Map<String, String> opciones = new HashMap<String, String>();
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].startsWith("--")) {
                opciones.put(args[i].substring(2), args[i + 1]);
            }
        }
        return opciones;
    }

    static String getOrDefault(Map<String, String> mapa, String clave, String valorDefecto) {
        String valor = mapa.get(clave);
        return valor != null ? valor : valorDefecto;
    }
}
