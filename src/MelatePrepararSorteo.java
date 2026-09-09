/**
 * MelatePrepararSorteo
 * -----------------------------------------------------------------------
 * Runner combinado: ejecuta en orden
 *   1) MelateAutoActualizador.ejecutarTodos() -> descarga y agrega sorteos
 *      nuevos de Melate Y Revancha
 *   2) AnalizadorMelate "generar" --producto MELATE   --dia miercoles
 *      AnalizadorMelate "generar" --producto REVANCHA --dia miercoles
 *      -> analiza cada historico actualizado (filtrado a sorteos que
 *         cayeron en miercoles, para preparar especificamente el proximo
 *         sorteo de esa noche) y genera jugadas sugeridas para ambos.
 *
 * Pensado para reemplazar la llamada directa a MelateAutoActualizador en la
 * tarea programada (Task Scheduler) los dias que corresponda, de modo que
 * cada corrida deje ambos historicos al dia Y genere de una vez las jugadas
 * sugeridas para Melate y Revancha en "jugadas_generadas.csv".
 *
 * Si se ejecuta manualmente de cara a otro dia de sorteo (viernes o
 * domingo), pasa el dia como primer argumento, ej.:
 *   java MelatePrepararSorteo viernes
 *
 * USO MANUAL:
 *   javac --release 17 -encoding UTF-8 *.java
 *   java MelatePrepararSorteo
 * -----------------------------------------------------------------------
 */
public class MelatePrepararSorteo {

    public static void main(String[] args) {
        String dia = args.length > 0 ? args[0] : "miercoles";

        System.out.println("=== PASO 1: Actualizando historicos (Melate y Revancha) ===");
        MelateAutoActualizador.ejecutarTodos();

        System.out.println();
        System.out.println("=== PASO 2: Generando jugadas para MELATE (sorteo del " + dia + ") ===");
        AnalizadorMelate.main(new String[] {
                "generar", "--producto", "MELATE", "--dia", dia
        });

        System.out.println();
        System.out.println("=== PASO 3: Generando jugadas para REVANCHA (sorteo del " + dia + ") ===");
        AnalizadorMelate.main(new String[] {
                "generar", "--producto", "REVANCHA", "--dia", dia
        });

        System.out.println();
        System.out.println("=== LISTO ===");
        System.out.println("Revisa 'jugadas_generadas.csv' (columna 'producto' distingue MELATE de REVANCHA).");
        System.out.println("Recuerda: Revancha se juega con la MISMA combinacion elegida para Melate; las");
        System.out.println("jugadas de REVANCHA aqui son un analisis independiente de su propio historico,");
        System.out.println("util para comparar, pero al comprar el boleto solo se elige una combinacion.");
    }
}
