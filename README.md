# RafaMatrix - Sistema de analisis Melate y Revancha

Reescritura completa y ordenada por capas de todo lo construido anteriormente
(actualizador, analizador de frecuencias, generador de jugadas y backtest),
soportando Melate y Revancha desde un solo programa.

**Compatible con Java 8** (se probo con javac/java 1.8.0_502 real antes de
entregarse). No usa records, HttpClient nuevo, Files.readString/writeString,
List.of/Map.of ni switch expressions - todo reescrito con las APIs
disponibles desde Java 8.

## Instalacion

1. Copia todos los `.java` de esta carpeta a tu proyecto de Eclipse
   (`C:\Users\Dudu\eclipse-workspace\Rafael\src` o donde prefieras),
   reemplazando los archivos sueltos anteriores (MelateAutoActualizador.java,
   MelatePrepararSorteo.java, BacktestMelate.java, etc. - quedaron
   integrados aqui).

2. Deja tu `historico_melate.txt` actual en esa misma carpeta (mismo
   formato oficial de 11 columnas, no cambia).

3. Compila:
   ```
   javac *.java
   ```

## Estructura del proyecto

| Archivo | Responsabilidad |
|---|---|
| `ProductoConfig.java` | Configuracion de un producto (URL, archivos, formato) |
| `Productos.java` | Catalogo central: MELATE y REVANCHA. Agregar Retro/Revanchita a futuro = una entrada aqui |
| `HistoricoRepository.java` | Unica clase que descarga CSVs (via HttpURLConnection) y lee/escribe los historicos |
| `AnalizadorEstadistico.java` | Frecuencias, pares, trios, ranking ponderado |
| `GeneradorJugadas.java` | Estrategias de generacion: CALIENTE, FRIO, BALANCEADA, ALEATORIA |
| `BacktestService.java` | Backtest walk-forward (ya validado: sin ventaja real sobre el azar) |
| `Main.java` | CLI que orquesta todo |

## Uso

```
# Todo en un paso (actualizar + analizar + generar jugadas) para Melate:
java Main --producto MELATE --accion todo

# Solo actualizar el historico de Revancha:
java Main --producto REVANCHA --accion actualizar

# Solo ver el analisis de frecuencias de Melate:
java Main --producto MELATE --accion analizar

# Solo generar 6 jugadas de Melate:
java Main --producto MELATE --accion jugadas --cantidad 6

# Correr el backtest de Revancha sobre los ultimos 800 sorteos:
java Main --producto REVANCHA --accion backtest --ventana 800
```

Sin argumentos (`java Main`), corre "todo" sobre MELATE por defecto.

## Archivos que genera/usa en tu carpeta

- `historico_melate.txt` / `historico_revancha.txt` - historico de cada producto
- `ultimo_concurso_melate.txt` / `ultimo_concurso_revancha.txt` - marcador interno,
  no lo edites a mano
- Formato Melate: `NPRODUCTO,CONCURSO,R1,R2,R3,R4,R5,R6,R7,BOLSA,FECHA` (11 columnas, con adicional)
- Formato Revancha: `NPRODUCTO,CONCURSO,R1,R2,R3,R4,R5,R6,BOLSA,FECHA` (10 columnas, sin adicional)

## Notas tecnicas (por que se ve distinto al Java moderno)

Como tu entorno usa Java 8, se evitaron a proposito:
- `record` -> se uso clase normal con getters
- `java.net.http.HttpClient` (Java 11+) -> se uso `HttpURLConnection` (Java 1.1+)
- `Files.readString`/`writeString` (Java 11+) -> se uso `Files.readAllLines`/`write`
- `List.of()`/`Map.of()` (Java 9+) -> se uso `ArrayList`/`HashMap` + `Collections.unmodifiable*`
- Switch expressions con `->` (Java 14+) -> switch tradicional con `case:`/`break`
- Lambdas para comparadores -> clases anonimas `new Comparator<T>() { ... }`
  (aunque Java 8 si soporta lambdas, se prefirio el estilo mas explicito
  para minimizar cualquier sorpresa de compilacion en tu entorno)

## Nota importante sobre el metodo

El `BacktestService` ya se corrio sobre tu historico real de Melate (4,250+
sorteos): el metodo de numeros "calientes" **no muestra ninguna ventaja real**
sobre una jugada aleatoria pura. Esto es esperado porque Melate es un sorteo
independiente y aleatorio en cada ocasion. El generador de jugadas sigue
siendo util por estructura/diversidad, no por ventaja predictiva real.
# RafaMatrix
