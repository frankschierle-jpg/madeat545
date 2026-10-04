package de.madeat545.app

import java.time.LocalDate

data class Recipe(
    val name: String,
    val minutes: Int,
    val kcal: Int,
    val ingredients: List<String>,
    val steps: List<String>,
)

/** Kalorienarme Mittagessen, max. 10 Minuten, Zutaten, die man meist zu Hause hat. kcal grob geschätzt. */
object Recipes {
    val all = listOf(
        Recipe(
            "Rührei mit Tomaten", 7, 350,
            listOf("2 Eier", "1 Tomate", "1 Scheibe Vollkornbrot", "Salz, Pfeffer", "1 TL Öl"),
            listOf("Tomate würfeln.", "Eier verquirlen, würzen.", "In der Pfanne mit Öl stocken lassen, Tomaten kurz mitbraten.", "Mit Brot essen."),
        ),
        Recipe(
            "Thunfisch-Gurken-Salat", 5, 300,
            listOf("1 Dose Thunfisch im eigenen Saft", "½ Gurke", "½ Zwiebel", "2 EL Joghurt", "Salz, Pfeffer, Zitrone"),
            listOf("Gurke und Zwiebel klein schneiden.", "Thunfisch abtropfen lassen.", "Alles mit Joghurt, Salz, Pfeffer und etwas Zitrone mischen."),
        ),
        Recipe(
            "Kräuterquark mit Gemüsesticks", 5, 320,
            listOf("250 g Magerquark", "1 Paprika oder Karotte", "½ Gurke", "Kräuter (TK oder getrocknet)", "Salz, Pfeffer", "1 Scheibe Vollkornbrot"),
            listOf("Quark mit etwas Wasser glatt rühren, mit Kräutern, Salz und Pfeffer würzen.", "Gemüse in Sticks schneiden.", "Zum Dippen mit Brot essen."),
        ),
        Recipe(
            "Omelett mit Spinat", 8, 330,
            listOf("2 Eier", "1 Handvoll TK-Spinat", "1 EL Feta oder Käse", "Salz, Pfeffer", "1 TL Öl"),
            listOf("Spinat in der Pfanne auftauen lassen.", "Verquirlte, gewürzte Eier darübergießen.", "Käse darüberstreuen, zugedeckt 3 Min. stocken lassen, zusammenklappen."),
        ),
        Recipe(
            "Hähnchen-Wrap", 10, 420,
            listOf("1 Vollkorn-Wrap", "100 g Hähnchen (Aufschnitt oder Reste)", "Salatblätter", "1 Tomate", "2 EL Joghurt", "Salz, Pfeffer"),
            listOf("Tomate in Scheiben schneiden.", "Wrap mit Joghurt bestreichen.", "Salat, Tomate und Hähnchen darauflegen, würzen und einrollen."),
        ),
        Recipe(
            "Linsen-Paprika-Pfanne", 8, 380,
            listOf("1 kleine Dose Linsen", "1 Paprika", "½ Zwiebel", "1 TL Öl", "Paprikapulver, Salz, Pfeffer"),
            listOf("Zwiebel und Paprika würfeln und im Öl 3 Min. anbraten.", "Abgetropfte Linsen dazugeben und 3 Min. erhitzen.", "Kräftig würzen."),
        ),
        Recipe(
            "Joghurt-Bowl mit Obst & Haferflocken", 4, 330,
            listOf("200 g Naturjoghurt (1,5 %)", "1 Apfel oder Banane", "3 EL Haferflocken", "Zimt"),
            listOf("Obst klein schneiden.", "Joghurt in eine Schüssel, Obst und Haferflocken darauf.", "Mit Zimt bestreuen."),
        ),
        Recipe(
            "Tomaten-Mozzarella-Brot", 5, 360,
            listOf("2 Scheiben Vollkornbrot", "½ Mozzarella (light)", "1 Tomate", "Basilikum oder Oregano", "Salz, Pfeffer"),
            listOf("Brot ggf. toasten.", "Mit Tomaten- und Mozzarellascheiben belegen.", "Würzen, fertig."),
        ),
    )

    fun forDay(store: Store, date: LocalDate): Recipe = all[store.recipeIndex(date)]
}

object Quotes {
    private val list = listOf(
        "Kleine Schritte jeden Tag ergeben große Veränderungen.",
        "Du musst nicht motiviert sein. Du musst nur anfangen.",
        "Dein zukünftiges Ich wird dir danken.",
        "Disziplin ist, das zu tun, was du dir vorgenommen hast – auch wenn du keine Lust hast.",
        "Glow-up beginnt mit einem gemachten Bett.",
        "Heute ist ein guter Tag, um stolz auf dich zu sein.",
        "Nicht perfekt. Aber dranbleiben.",
        "Du bist stärker als deine Ausreden.",
        "Jeder Haken ist ein Versprechen, das du dir selbst hältst.",
        "Früh aufstehen, Ziele angehen.",
    )

    fun forDay(date: LocalDate): String = list[Math.floorMod(date.toEpochDay(), list.size.toLong()).toInt()]
}
