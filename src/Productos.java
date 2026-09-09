import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Productos
 * -----------------------------------------------------------------------
 * Catalogo central de productos soportados. Para agregar Retro o
 * Revanchita en el futuro, solo se anade una linea aqui.
 *
 * (Version compatible con Java 8: sin List.of/Map.of, que son Java 9+)
 * -----------------------------------------------------------------------
 */
public final class Productos {

    public static final ProductoConfig MELATE = new ProductoConfig(
            "MELATE",
            "https://www.loterianacional.gob.mx/Documentos/Historicos/Melate.csv",
            "historico_melate.txt",
            "ultimo_concurso_melate.txt",
            6, 56, true,
            130, 190
    );

    public static final ProductoConfig REVANCHA = new ProductoConfig(
            "REVANCHA",
            "https://www.loterianacional.gob.mx/Documentos/Historicos/Revancha.csv",
            "historico_revancha.txt",
            "ultimo_concurso_revancha.txt",
            6, 56, false,
            130, 190
    );

    public static final List<ProductoConfig> TODOS;
    private static final Map<String, ProductoConfig> POR_NOMBRE;

    static {
        List<ProductoConfig> lista = new ArrayList<ProductoConfig>();
        lista.add(MELATE);
        lista.add(REVANCHA);
        TODOS = Collections.unmodifiableList(lista);

        Map<String, ProductoConfig> mapa = new HashMap<String, ProductoConfig>();
        mapa.put("MELATE", MELATE);
        mapa.put("REVANCHA", REVANCHA);
        POR_NOMBRE = Collections.unmodifiableMap(mapa);
    }

    public static ProductoConfig porNombre(String nombre) {
        ProductoConfig p = POR_NOMBRE.get(nombre.toUpperCase());
        if (p == null) {
            throw new IllegalArgumentException("Producto desconocido: " + nombre +
                    " (opciones: MELATE, REVANCHA)");
        }
        return p;
    }

    private Productos() {}
}
