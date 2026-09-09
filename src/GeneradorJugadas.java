import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * GeneradorJugadas
 * -----------------------------------------------------------------------
 * Genera combinaciones de numeros bajo distintas estrategias, todas
 * filtradas por rango de suma y diversidad minima de decenas para evitar
 * combinaciones muy agrupadas.
 *
 * Estrategias disponibles:
 *   CALIENTE    - favorece numeros que mas se han repetido historicamente
 *   FRIO        - favorece numeros que menos se han repetido
 *   BALANCEADA  - mezcla de calientes y frios
 *   ALEATORIA   - control, sin ponderar nada (equivalente estadistico
 *                 al sorteo real)
 *
 * Recordatorio: bajo la hipotesis (confirmada por BacktestService) de que
 * Melate es independiente sorteo a sorteo, ninguna estrategia tiene mas
 * probabilidad real que otra. Se ofrecen por variedad/estructura, no por
 * ventaja predictiva.
 *
 * (Version compatible con Java 8: switch tradicional en vez de switch
 * expression con "->", que requiere Java 14+)
 * -----------------------------------------------------------------------
 */
public class GeneradorJugadas {

    public enum Estrategia { CALIENTE, FRIO, BALANCEADA, ALEATORIA }

    private static final int DIVERSIDAD_MIN_DECENAS = 3;
    private static final int MAX_INTENTOS = 200000;

    private final ProductoConfig producto;
    private final Random random = new Random();

    public GeneradorJugadas(ProductoConfig producto) {
        this.producto = producto;
    }

    public int[] generar(Estrategia estrategia, int[] frecuencia) {
        switch (estrategia) {
            case CALIENTE:
                return generarPonderada(frecuencia, true);
            case FRIO:
                return generarPonderada(frecuencia, false);
            case BALANCEADA:
                return generarBalanceada(frecuencia);
            case ALEATORIA:
                return generarAleatoriaFiltrada();
            default:
                throw new IllegalArgumentException("Estrategia no soportada: " + estrategia);
        }
    }

    /** Genera "cantidad" jugadas distintas con la estrategia dada. */
    public List<int[]> generarVarias(Estrategia estrategia, int[] frecuencia, int cantidad) {
        List<int[]> jugadas = new ArrayList<int[]>();
        int intentos = 0;
        while (jugadas.size() < cantidad && intentos < MAX_INTENTOS) {
            intentos++;
            int[] candidata = generar(estrategia, frecuencia);
            if (!contiene(jugadas, candidata)) jugadas.add(candidata);
        }
        return jugadas;
    }

    private int[] generarPonderada(final int[] frecuencia, final boolean preferirCalientes) {
        int maxFreq = 1;
        for (int f : frecuencia) if (f > maxFreq) maxFreq = f;
        final int maxFreqFinal = maxFreq;

        List<Integer> pool = new ArrayList<Integer>();
        for (int n = 1; n <= producto.numeroMaximo(); n++) {
            int peso = preferirCalientes ? frecuencia[n] + 1 : (maxFreqFinal - frecuencia[n] + 1);
            peso = Math.max(1, peso);
            for (int i = 0; i < peso; i++) pool.add(n);
        }
        return sortearConFiltro(pool);
    }

    private int[] generarBalanceada(final int[] frecuencia) {
        List<Integer> ordenados = new ArrayList<Integer>();
        for (int n = 1; n <= producto.numeroMaximo(); n++) ordenados.add(n);
        java.util.Collections.sort(ordenados, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return frecuencia[b] - frecuencia[a];
            }
        });

        int mitad = Math.max(5, ordenados.size() / 4);
        int intentos = 0;
        while (intentos < MAX_INTENTOS) {
            intentos++;
            Set<Integer> combinacion = new TreeSet<Integer>();
            while (combinacion.size() < producto.numerosPorSorteo() / 2) {
                combinacion.add(ordenados.get(random.nextInt(mitad)));
            }
            while (combinacion.size() < producto.numerosPorSorteo()) {
                combinacion.add(ordenados.get(ordenados.size() - 1 - random.nextInt(mitad)));
            }
            int[] arr = toArray(combinacion);
            if (cumpleFiltro(arr)) return arr;
        }
        return generarAleatoriaFiltrada(); // respaldo si no encontro una valida a tiempo
    }

    private int[] generarAleatoriaFiltrada() {
        List<Integer> pool = new ArrayList<Integer>();
        for (int n = 1; n <= producto.numeroMaximo(); n++) pool.add(n);
        return sortearConFiltro(pool);
    }

    private int[] sortearConFiltro(List<Integer> pool) {
        int intentos = 0;
        while (intentos < MAX_INTENTOS) {
            intentos++;
            Set<Integer> combinacion = new TreeSet<Integer>();
            while (combinacion.size() < producto.numerosPorSorteo()) {
                combinacion.add(pool.get(random.nextInt(pool.size())));
            }
            int[] arr = toArray(combinacion);
            if (cumpleFiltro(arr)) return arr;
        }
        // si no se encontro nada dentro del filtro tras muchos intentos, se regresa igual
        // la ultima combinacion generada para no bloquear el programa
        Set<Integer> combinacion = new TreeSet<Integer>();
        while (combinacion.size() < producto.numerosPorSorteo()) {
            combinacion.add(pool.get(random.nextInt(pool.size())));
        }
        return toArray(combinacion);
    }

    private boolean cumpleFiltro(int[] arr) {
        int suma = 0;
        for (int n : arr) suma += n;
        if (suma < producto.sumaFiltroMin() || suma > producto.sumaFiltroMax()) return false;
        Set<Integer> decenas = new HashSet<Integer>();
        for (int n : arr) decenas.add(n / 10);
        return decenas.size() >= DIVERSIDAD_MIN_DECENAS;
    }

    private boolean contiene(List<int[]> jugadas, int[] candidata) {
        for (int[] j : jugadas) if (Arrays.equals(j, candidata)) return true;
        return false;
    }

    private int[] toArray(Set<Integer> set) {
        int[] arr = new int[set.size()];
        int i = 0;
        for (int n : set) arr[i++] = n;
        return arr;
    }
}
