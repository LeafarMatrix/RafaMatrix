/**
 * ProductoConfig
 * -----------------------------------------------------------------------
 * Define todo lo que distingue a un producto de Loteria Nacional
 * (Melate, Revancha, y en el futuro Retro/Revanchita): de donde se
 * descarga su historico oficial, en que archivos locales se guarda, y
 * el formato de sus columnas.
 *
 * Agregar un producto nuevo en el futuro es tan simple como anadir una
 * entrada mas en Productos.TODOS, sin tocar el resto del codigo.
 *
 * (Version compatible con Java 8: clase normal en vez de record)
 * -----------------------------------------------------------------------
 */
public class ProductoConfig {

    private final String nombre;
    private final String urlCsv;
    private final String archivoHistorico;
    private final String archivoMarcador;
    private final int numerosPorSorteo;
    private final int numeroMaximo;
    private final boolean tieneAdicional;
    private final int sumaFiltroMin;
    private final int sumaFiltroMax;

    public ProductoConfig(String nombre, String urlCsv, String archivoHistorico,
                           String archivoMarcador, int numerosPorSorteo, int numeroMaximo,
                           boolean tieneAdicional, int sumaFiltroMin, int sumaFiltroMax) {
        this.nombre = nombre;
        this.urlCsv = urlCsv;
        this.archivoHistorico = archivoHistorico;
        this.archivoMarcador = archivoMarcador;
        this.numerosPorSorteo = numerosPorSorteo;
        this.numeroMaximo = numeroMaximo;
        this.tieneAdicional = tieneAdicional;
        this.sumaFiltroMin = sumaFiltroMin;
        this.sumaFiltroMax = sumaFiltroMax;
    }

    public String nombre() { return nombre; }
    public String urlCsv() { return urlCsv; }
    public String archivoHistorico() { return archivoHistorico; }
    public String archivoMarcador() { return archivoMarcador; }
    public int numerosPorSorteo() { return numerosPorSorteo; }
    public int numeroMaximo() { return numeroMaximo; }
    public boolean tieneAdicional() { return tieneAdicional; }
    public int sumaFiltroMin() { return sumaFiltroMin; }
    public int sumaFiltroMax() { return sumaFiltroMax; }

    /** Cuantas columnas trae el CSV oficial de este producto (incluyendo NPRODUCTO y CONCURSO). */
    public int columnasCsv() {
        return tieneAdicional ? 11 : 10;
    }

    public String encabezadoOficial() {
        return tieneAdicional
                ? "NPRODUCTO,CONCURSO,R1,R2,R3,R4,R5,R6,R7,BOLSA,FECHA"
                : "NPRODUCTO,CONCURSO,R1,R2,R3,R4,R5,R6,BOLSA,FECHA";
    }
}
