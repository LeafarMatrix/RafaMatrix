import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AnalizadorEstadistico
 * -----------------------------------------------------------------------
 * Estadistica descriptiva pura sobre un historico de sorteos: frecuencia
 * individual, pares/trios mas repetidos y un ranking ponderado.
 *
 * IMPORTANTE (confirmado con BacktestService sobre el historico real de
 * Melate): estos patrones son del pasado y no dan ninguna ventaja
 * predictiva sobre un sorteo futuro, porque cada sorteo es independiente.
 * Se mantienen porque son utiles para diversidad/estructura de jugadas,
 * no porque prediquen el resultado.
 * -----------------------------------------------------------------------
 */
public class AnalizadorEstadistico {

    private final ProductoConfig producto;

    public AnalizadorEstadistico(ProductoConfig producto) {
        this.producto = producto;
    }

    public int[] frecuencia(List<int[]> sorteos) {
        int[] frecuencia = new int[producto.numeroMaximo() + 1];
        for (int[] s : sorteos) {
            for (int n : s) frecuencia[n]++;
        }
        return frecuencia;
    }

    public Map<String, Integer> pares(List<int[]> sorteos) {
        Map<String, Integer> conteo = new HashMap<String, Integer>();
        for (int[] s : sorteos) {
            int[] ord = s.clone();
            Arrays.sort(ord);
            for (int i = 0; i < ord.length; i++) {
                for (int j = i + 1; j < ord.length; j++) {
                    incrementar(conteo, ord[i] + "," + ord[j]);
                }
            }
        }
        return conteo;
    }

    public Map<String, Integer> trios(List<int[]> sorteos) {
        Map<String, Integer> conteo = new HashMap<String, Integer>();
        for (int[] s : sorteos) {
            int[] ord = s.clone();
            Arrays.sort(ord);
            for (int i = 0; i < ord.length; i++) {
                for (int j = i + 1; j < ord.length; j++) {
                    for (int k = j + 1; k < ord.length; k++) {
                        incrementar(conteo, ord[i] + "," + ord[j] + "," + ord[k]);
                    }
                }
            }
        }
        return conteo;
    }

    /** Peso ponderado por numero: 60% frecuencia individual, 25% fuerza en pares, 15% fuerza en trios. */
    public int[] pesoPonderado(int[] frecuencia, Map<String, Integer> pares, Map<String, Integer> trios) {
        int max = producto.numeroMaximo();
        double[] bonusPar = new double[max + 1];
        for (Map.Entry<String, Integer> e : pares.entrySet()) {
            String[] nums = e.getKey().split(",");
            bonusPar[Integer.parseInt(nums[0])] += e.getValue();
            bonusPar[Integer.parseInt(nums[1])] += e.getValue();
        }
        double[] bonusTrio = new double[max + 1];
        for (Map.Entry<String, Integer> e : trios.entrySet()) {
            for (String s : e.getKey().split(",")) {
                bonusTrio[Integer.parseInt(s)] += e.getValue();
            }
        }

        double maxFreq = 1;
        for (int f : frecuencia) if (f > maxFreq) maxFreq = f;
        double maxPar = 1;
        for (double b : bonusPar) if (b > maxPar) maxPar = b;
        double maxTrio = 1;
        for (double b : bonusTrio) if (b > maxTrio) maxTrio = b;

        int[] peso = new int[max + 1];
        for (int n = 1; n <= max; n++) {
            double score = 0.6 * (frecuencia[n] / maxFreq)
                         + 0.25 * (bonusPar[n] / maxPar)
                         + 0.15 * (bonusTrio[n] / maxTrio);
            peso[n] = (int) Math.round(score * 1000);
        }
        return peso;
    }

    /** Numeros ordenados de mas a menos frecuentes. */
    public List<Integer> ordenadosPorFrecuencia(final int[] frecuencia) {
        List<Integer> nums = new ArrayList<Integer>();
        for (int n = 1; n <= producto.numeroMaximo(); n++) nums.add(n);
        java.util.Collections.sort(nums, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return frecuencia[b] - frecuencia[a];
            }
        });
        return nums;
    }

    private void incrementar(Map<String, Integer> mapa, String clave) {
        Integer actual = mapa.get(clave);
        mapa.put(clave, actual == null ? 1 : actual + 1);
    }
}
