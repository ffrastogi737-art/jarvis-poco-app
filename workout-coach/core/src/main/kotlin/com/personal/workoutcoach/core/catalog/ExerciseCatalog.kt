package com.personal.workoutcoach.core.catalog

import com.personal.workoutcoach.core.catalog.Pattern.*
import com.personal.workoutcoach.core.equipment.Capability.*

/**
 * Locally stored exercise catalogue. IDs are stable: workout history and AI recommendations
 * reference them. Instructions are general technique guidance, not medical advice.
 * No video links are included on purpose (none have been verified).
 */
object ExerciseCatalog {
    private fun p(sets: Int, min: Int, max: Int, rest: Int) = Prescription(sets, min, max, null, rest)
    private fun t(sets: Int, sec: Int, rest: Int) = Prescription(sets, seconds = sec, restSeconds = rest)

    val all: List<Exercise> = listOf(
        // ---- Chest / horizontal push
        Exercise(
            "db_floor_press", "Dumbbell Floor Press", listOf("Chest", "Triceps", "Front delts"), HORIZONTAL_PUSH,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 1, isCompound = true, start = p(3, 8, 12, 90),
            instructions = listOf("Lie on your back, knees bent, a dumbbell in each hand.", "Start with arms straight over your chest.", "Lower until your upper arms touch the floor, pause lightly.", "Press back up."),
            cues = listOf("Elbows about 45° from the body", "Pause softly on the floor, no bouncing"),
            commonErrors = listOf("Flaring elbows to 90°", "Dropping the weights onto the floor"),
            easier = listOf("db_floor_press_single", "incline_push_up"), harder = listOf("bench_db_press"),
            safetyNotes = listOf("Bring the dumbbells to your chest before lying back, and roll to the side to put them down."),
            aliases = listOf("floor press", "chest press"),
        ),
        Exercise(
            "db_floor_press_single", "Single-Arm Dumbbell Floor Press", listOf("Chest", "Triceps", "Core"), HORIZONTAL_PUSH,
            setOf(DUMBBELL), LoadKind.DUMBBELL_SINGLE, 1, perSide = true, isCompound = true, start = p(2, 8, 12, 75),
            instructions = listOf("Lie on your back holding one dumbbell.", "Press it up over your shoulder.", "Lower until the upper arm touches the floor.", "Finish all reps, then switch sides."),
            cues = listOf("Keep hips level", "Free hand on the floor for balance"),
            commonErrors = listOf("Twisting the torso to push"),
            harder = listOf("db_floor_press"),
        ),
        Exercise(
            "bench_db_press", "Flat Bench Dumbbell Press", listOf("Chest", "Triceps", "Front delts"), HORIZONTAL_PUSH,
            setOf(DUMBBELL_PAIR, BENCH_FLAT), LoadKind.DUMBBELL_PAIR, 2, isCompound = true, start = p(3, 8, 12, 90),
            instructions = listOf("Sit on the bench with dumbbells on your thighs, then lie back.", "Press the dumbbells above your chest.", "Lower with control to chest level.", "Press up."),
            cues = listOf("Feet flat on the floor", "Shoulder blades pulled back"),
            commonErrors = listOf("Bouncing at the bottom", "Lifting hips off the bench"),
            easier = listOf("db_floor_press"),
        ),
        Exercise(
            "push_up", "Push-Up", listOf("Chest", "Triceps", "Core"), HORIZONTAL_PUSH,
            emptySet(), LoadKind.BODYWEIGHT, 2, isCompound = true, start = p(2, 6, 12, 75),
            instructions = listOf("Hands slightly wider than shoulders, body in a straight line.", "Lower your chest towards the floor.", "Push back up."),
            cues = listOf("Squeeze glutes, keep a straight line", "Elbows about 45°"),
            commonErrors = listOf("Hips sagging", "Partial range of motion"),
            easier = listOf("incline_push_up", "knee_push_up"), harder = listOf("deficit_push_up"),
            aliases = listOf("pushup", "push up"),
        ),
        Exercise(
            "incline_push_up", "Incline Push-Up", listOf("Chest", "Triceps"), HORIZONTAL_PUSH,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = true, start = p(2, 8, 15, 60),
            instructions = listOf("Place hands on a sturdy, non-sliding surface (table edge, counter, stairs).", "Walk feet back so your body is straight.", "Lower chest to the edge, push back."),
            cues = listOf("Higher surface = easier"),
            commonErrors = listOf("Using a surface that can slide or tip"),
            harder = listOf("push_up"),
            safetyNotes = listOf("Only use a surface that cannot move or tip over."),
        ),
        Exercise(
            "knee_push_up", "Knee Push-Up", listOf("Chest", "Triceps"), HORIZONTAL_PUSH,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = true, start = p(2, 8, 15, 60),
            instructions = listOf("Push-up position with knees on the floor.", "Keep a straight line from knees to head.", "Lower and push up."),
            cues = listOf("Don't pike the hips"), commonErrors = listOf("Bending at the hips"),
            harder = listOf("push_up"),
        ),
        Exercise(
            "deficit_push_up", "Deficit Push-Up (handles)", listOf("Chest", "Triceps"), HORIZONTAL_PUSH,
            setOf(PUSH_UP_HANDLES), LoadKind.BODYWEIGHT, 3, isCompound = true, start = p(3, 6, 12, 90),
            instructions = listOf("Hold push-up handles, body straight.", "Lower below hand level with control.", "Push up."),
            cues = listOf("Control the bottom stretch"), commonErrors = listOf("Dropping into the bottom"),
            easier = listOf("push_up"),
        ),
        Exercise(
            "close_grip_push_up", "Close-Grip Push-Up", listOf("Triceps", "Chest"), TRICEPS,
            emptySet(), LoadKind.BODYWEIGHT, 2, isCompound = true, start = p(2, 6, 12, 75),
            instructions = listOf("Push-up position, hands under shoulders.", "Lower keeping elbows close to your ribs.", "Push up."),
            cues = listOf("Elbows brush your sides"), commonErrors = listOf("Hands too close (wrist strain)"),
            easier = listOf("incline_push_up"),
            safetyNotes = listOf("Stop if you feel wrist or elbow pain; use an incline instead."),
        ),
        // ---- Back / pulls
        Exercise(
            "db_row_one_arm", "One-Arm Dumbbell Row", listOf("Lats", "Upper back", "Biceps"), HORIZONTAL_PULL,
            setOf(DUMBBELL), LoadKind.DUMBBELL_SINGLE, 1, perSide = true, isCompound = true, start = p(3, 8, 12, 75),
            instructions = listOf("Staggered stance, free hand on your front knee or a sturdy chair.", "Let the dumbbell hang under your shoulder.", "Pull it towards your hip.", "Lower slowly. Finish one side, then switch."),
            cues = listOf("Flat back, neck neutral", "Pull with the elbow, not the hand"),
            commonErrors = listOf("Twisting the torso", "Rounding the lower back"),
            easier = listOf("prone_y_t_raise"), harder = listOf("db_bent_over_row"),
            aliases = listOf("dumbbell row", "row"),
        ),
        Exercise(
            "db_bent_over_row", "Bent-Over Dumbbell Row", listOf("Upper back", "Lats", "Biceps"), HORIZONTAL_PULL,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 2, isCompound = true, start = p(3, 8, 12, 90),
            instructions = listOf("Hinge at the hips with a flat back, dumbbells hanging.", "Row both dumbbells to your lower ribs.", "Lower with control."),
            cues = listOf("Brace your core", "Torso stays still"),
            commonErrors = listOf("Standing up during the pull", "Rounded back"),
            easier = listOf("db_row_one_arm"),
            safetyNotes = listOf("Choose the one-arm row if your lower back feels tired."),
        ),
        Exercise(
            "db_pullover", "Dumbbell Floor Pullover", listOf("Lats", "Chest"), HORIZONTAL_PULL,
            setOf(DUMBBELL), LoadKind.DUMBBELL_SINGLE, 2, isCompound = false, maxLoadKg = 15.0, start = p(2, 10, 12, 60),
            instructions = listOf("Lie on your back holding one dumbbell with both hands above your chest.", "Lower it behind your head with slightly bent elbows until your arms are near the floor.", "Pull it back over your chest."),
            cues = listOf("Ribs down", "Move only at the shoulders"),
            commonErrors = listOf("Arching the lower back", "Bending elbows too much"),
            safetyNotes = listOf("Skip this if you have any shoulder discomfort overhead."),
        ),
        Exercise(
            "prone_y_t_raise", "Prone Y-T Raise", listOf("Upper back", "Rear delts"), HORIZONTAL_PULL,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = false, start = p(2, 8, 12, 45),
            instructions = listOf("Lie face down, arms overhead in a Y.", "Lift arms a few centimetres, squeezing shoulder blades.", "Lower, move arms to a T, repeat."),
            cues = listOf("Thumbs up", "Small, controlled lift"), commonErrors = listOf("Lifting with the lower back"),
            harder = listOf("db_row_one_arm"),
        ),
        Exercise(
            "band_row", "Anchored Band Row", listOf("Upper back", "Lats"), HORIZONTAL_PULL,
            setOf(BAND_ANCHORED), LoadKind.BAND, 1, isCompound = true, start = p(3, 10, 15, 60),
            instructions = listOf("Attach the band at chest height with a proper anchor.", "Step back until there is tension.", "Row the handles to your ribs, pause, return."),
            cues = listOf("Shoulders down and back"), commonErrors = listOf("Leaning back to pull"),
            safetyNotes = listOf("Check the anchor and the band for tears before every set."),
        ),
        Exercise(
            "band_pull_apart", "Band Pull-Apart", listOf("Rear delts", "Upper back"), SHOULDER_ISOLATION,
            setOf(BAND), LoadKind.BAND, 1, isCompound = false, start = p(2, 12, 20, 45),
            instructions = listOf("Hold the band at shoulder width, arms straight in front.", "Pull it apart until it touches your chest.", "Return slowly."),
            cues = listOf("Arms stay straight"), commonErrors = listOf("Shrugging"),
        ),
        Exercise(
            "pull_up", "Pull-Up", listOf("Lats", "Biceps", "Upper back"), VERTICAL_PULL,
            setOf(PULL_UP_BAR), LoadKind.BODYWEIGHT, 3, isCompound = true, start = p(3, 3, 8, 120),
            instructions = listOf("Hang with hands just wider than shoulders.", "Pull until your chin passes the bar.", "Lower all the way with control."),
            cues = listOf("Lead with the chest"), commonErrors = listOf("Kipping", "Half reps"),
            easier = listOf("negative_pull_up"),
            safetyNotes = listOf("Check that the bar is secure before each session."),
        ),
        Exercise(
            "negative_pull_up", "Negative Pull-Up", listOf("Lats", "Biceps"), VERTICAL_PULL,
            setOf(PULL_UP_BAR), LoadKind.BODYWEIGHT, 2, isCompound = true, start = p(2, 3, 5, 90),
            instructions = listOf("Step up (stable chair) so your chin is above the bar.", "Lower yourself over 3–5 seconds.", "Step back up and repeat."),
            cues = listOf("Slow and controlled"), commonErrors = listOf("Dropping quickly"),
            harder = listOf("pull_up"),
            safetyNotes = listOf("Check that the bar is secure; use a stable step."),
        ),
        // ---- Shoulders
        Exercise(
            "db_shoulder_press", "Dumbbell Shoulder Press", listOf("Shoulders", "Triceps"), VERTICAL_PUSH,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 3, isCompound = true, start = p(3, 6, 10, 90),
            instructions = listOf("Stand, dumbbells at shoulder height.", "Press overhead until arms are straight.", "Lower to shoulders."),
            cues = listOf("Squeeze glutes, ribs down"), commonErrors = listOf("Leaning back"),
            easier = listOf("db_shoulder_press_single", "pike_push_up"),
            aliases = listOf("overhead press", "shoulder press", "ohp"),
        ),
        Exercise(
            "db_shoulder_press_single", "Half-Kneeling Single-Arm Press", listOf("Shoulders", "Triceps", "Core"), VERTICAL_PUSH,
            setOf(DUMBBELL), LoadKind.DUMBBELL_SINGLE, 2, perSide = true, isCompound = true, start = p(2, 6, 10, 75),
            instructions = listOf("Kneel on one knee, dumbbell at the shoulder on the same side as the down knee.", "Press overhead.", "Lower with control. Finish reps, switch sides."),
            cues = listOf("Stay tall, don't lean"), commonErrors = listOf("Arching the lower back"),
            easier = listOf("pike_push_up"), harder = listOf("db_shoulder_press"),
            safetyNotes = listOf("Use a cushion or mat under the knee."),
        ),
        Exercise(
            "pike_push_up", "Pike Push-Up", listOf("Shoulders", "Triceps"), VERTICAL_PUSH,
            emptySet(), LoadKind.BODYWEIGHT, 2, isCompound = true, start = p(2, 5, 10, 75),
            instructions = listOf("Start in a downward-dog position, hips high.", "Bend elbows to bring your head towards the floor.", "Push back up."),
            cues = listOf("Head moves forward of the hands"), commonErrors = listOf("Hips dropping"),
            harder = listOf("db_shoulder_press_single"),
        ),
        Exercise(
            "db_lateral_raise", "Dumbbell Lateral Raise", listOf("Side delts"), SHOULDER_ISOLATION,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 1, isCompound = false, maxLoadKg = 10.0, start = p(2, 10, 15, 60),
            instructions = listOf("Stand with light dumbbells at your sides.", "Raise arms out to shoulder height, slight elbow bend.", "Lower slowly."),
            cues = listOf("Lead with elbows"), commonErrors = listOf("Swinging the weight", "Shrugging"),
            easier = listOf("band_lateral_raise"),
        ),
        Exercise(
            "db_rear_delt_fly", "Dumbbell Rear-Delt Fly", listOf("Rear delts", "Upper back"), SHOULDER_ISOLATION,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 1, isCompound = false, maxLoadKg = 10.0, start = p(2, 10, 15, 60),
            instructions = listOf("Hinge forward with a flat back, light dumbbells hanging.", "Raise arms out to the sides.", "Lower slowly."),
            cues = listOf("Pinkies slightly up"), commonErrors = listOf("Using momentum"),
            easier = listOf("prone_y_t_raise"),
        ),
        Exercise(
            "band_lateral_raise", "Band Lateral Raise", listOf("Side delts"), SHOULDER_ISOLATION,
            setOf(BAND), LoadKind.BAND, 1, isCompound = false, start = p(2, 12, 20, 45),
            instructions = listOf("Stand on the band, ends in hands.", "Raise arms out to shoulder height.", "Lower slowly."),
            cues = listOf("Slight elbow bend"), commonErrors = listOf("Shrugging"),
            safetyNotes = listOf("Keep the band securely under your feet."),
        ),
        // ---- Arms
        Exercise(
            "db_curl", "Dumbbell Curl", listOf("Biceps"), BICEPS,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 2, isCompound = false, maxLoadKg = 20.0, start = p(2, 6, 10, 60),
            instructions = listOf("Stand with dumbbells at your sides, palms forward.", "Curl up without moving your elbows forward.", "Lower slowly."),
            cues = listOf("Elbows pinned to your sides"), commonErrors = listOf("Swinging the torso"),
            easier = listOf("hammer_curl", "band_curl"),
            safetyNotes = listOf("With heavy fixed dumbbells, alternate arms and keep reps strict."),
            aliases = listOf("curl", "bicep curl", "biceps curl"),
        ),
        Exercise(
            "hammer_curl", "Hammer Curl", listOf("Biceps", "Forearms"), BICEPS,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 1, isCompound = false, maxLoadKg = 22.5, start = p(2, 6, 12, 60),
            instructions = listOf("Hold dumbbells with palms facing each other.", "Curl up, keeping elbows still.", "Lower slowly. Alternating arms is fine."),
            cues = listOf("Neutral wrist"), commonErrors = listOf("Swinging"),
        ),
        Exercise(
            "band_curl", "Band Curl", listOf("Biceps"), BICEPS,
            setOf(BAND), LoadKind.BAND, 1, isCompound = false, start = p(2, 12, 20, 45),
            instructions = listOf("Stand on the band, ends in hands.", "Curl up.", "Lower slowly."),
            cues = listOf("Elbows still"), commonErrors = listOf("Leaning back"),
        ),
        Exercise(
            "db_overhead_triceps_ext", "Dumbbell Overhead Triceps Extension", listOf("Triceps"), TRICEPS,
            setOf(DUMBBELL), LoadKind.DUMBBELL_SINGLE, 2, isCompound = false, maxLoadKg = 20.0, start = p(2, 8, 12, 60),
            instructions = listOf("Hold one dumbbell overhead with both hands (sitting or standing).", "Lower it behind your head by bending the elbows.", "Extend back up."),
            cues = listOf("Elbows point forward", "Ribs down"), commonErrors = listOf("Elbows flaring wide", "Arching the back"),
            easier = listOf("close_grip_push_up"),
            safetyNotes = listOf("Grip the dumbbell head securely; stop if you feel elbow pain."),
            aliases = listOf("triceps extension", "overhead extension"),
        ),
        Exercise(
            "dips", "Parallel Bar Dip", listOf("Triceps", "Chest"), TRICEPS,
            setOf(DIP_STATION), LoadKind.BODYWEIGHT, 3, isCompound = true, start = p(2, 4, 8, 90),
            instructions = listOf("Support yourself on the bars, arms straight.", "Lower until upper arms are about parallel to the floor.", "Press up."),
            cues = listOf("Shoulders away from ears"), commonErrors = listOf("Going too deep"),
            easier = listOf("close_grip_push_up"),
            safetyNotes = listOf("Stop if you feel pain at the front of the shoulder."),
        ),
        // ---- Legs
        Exercise(
            "goblet_squat", "Goblet Squat", listOf("Quads", "Glutes", "Core"), SQUAT,
            setOf(DUMBBELL), LoadKind.DUMBBELL_SINGLE, 1, isCompound = true, start = p(3, 8, 12, 90),
            instructions = listOf("Hold one dumbbell vertically against your chest.", "Feet about shoulder width.", "Sit down between your heels, chest up.", "Stand back up."),
            cues = listOf("Knees track over toes", "Whole foot on the floor"),
            commonErrors = listOf("Heels lifting", "Rounding the back at the bottom"),
            easier = listOf("bodyweight_squat"), harder = listOf("db_split_squat"),
            aliases = listOf("squat", "goblet"),
        ),
        Exercise(
            "bodyweight_squat", "Bodyweight Squat", listOf("Quads", "Glutes"), SQUAT,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = true, start = p(2, 10, 20, 60),
            instructions = listOf("Feet shoulder width.", "Sit down and back to a comfortable depth.", "Stand up."),
            cues = listOf("Chest up"), commonErrors = listOf("Knees caving in"),
            harder = listOf("goblet_squat"),
        ),
        Exercise(
            "kb_goblet_squat", "Kettlebell Goblet Squat", listOf("Quads", "Glutes"), SQUAT,
            setOf(KETTLEBELL), LoadKind.KETTLEBELL, 1, isCompound = true, start = p(3, 8, 12, 90),
            instructions = listOf("Hold the kettlebell by the horns at chest height.", "Squat down between your heels.", "Stand up."),
            cues = listOf("Elbows inside knees at the bottom"), commonErrors = listOf("Heels lifting"),
        ),
        Exercise(
            "db_rdl", "Dumbbell Romanian Deadlift", listOf("Hamstrings", "Glutes", "Lower back"), HINGE,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 2, isCompound = true, start = p(3, 8, 12, 90),
            instructions = listOf("Stand holding dumbbells in front of your thighs.", "Push hips back with soft knees, sliding the weights down your legs.", "Stop when you feel a hamstring stretch with a flat back.", "Drive hips forward to stand."),
            cues = listOf("Flat back throughout", "Weights stay close to legs"),
            commonErrors = listOf("Rounding the back", "Squatting instead of hinging"),
            easier = listOf("glute_bridge"),
            safetyNotes = listOf("Only go as low as you can with a flat back."),
            aliases = listOf("rdl", "deadlift", "romanian deadlift"),
        ),
        Exercise(
            "glute_bridge", "Glute Bridge", listOf("Glutes", "Hamstrings"), HINGE,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = true, start = p(2, 10, 15, 60),
            instructions = listOf("Lie on your back, knees bent, feet flat.", "Drive through heels to lift hips.", "Squeeze glutes at the top, lower."),
            cues = listOf("Ribs down, no back arch"), commonErrors = listOf("Pushing through toes"),
            harder = listOf("db_rdl"),
        ),
        Exercise(
            "kb_deadlift", "Kettlebell Deadlift", listOf("Glutes", "Hamstrings"), HINGE,
            setOf(KETTLEBELL), LoadKind.KETTLEBELL, 1, isCompound = true, start = p(3, 8, 12, 90),
            instructions = listOf("Kettlebell between your feet.", "Hinge down with a flat back and grip the handle.", "Stand up by driving hips forward."),
            cues = listOf("Flat back"), commonErrors = listOf("Rounding"),
        ),
        Exercise(
            "barbell_rdl", "Barbell Romanian Deadlift", listOf("Hamstrings", "Glutes"), HINGE,
            setOf(BARBELL), LoadKind.BARBELL, 2, isCompound = true, start = p(3, 8, 10, 120),
            instructions = listOf("Lift the bar from the floor to standing first.", "Push hips back, bar sliding down the thighs.", "Stand back up."),
            cues = listOf("Bar close to legs"), commonErrors = listOf("Rounded back"),
            easier = listOf("db_rdl"),
        ),
        Exercise(
            "barbell_row", "Barbell Bent-Over Row", listOf("Upper back", "Lats"), HORIZONTAL_PULL,
            setOf(BARBELL), LoadKind.BARBELL, 2, isCompound = true, start = p(3, 8, 10, 120),
            instructions = listOf("Hinge forward with a flat back holding the bar.", "Row to your lower ribs.", "Lower with control."),
            cues = listOf("Torso still"), commonErrors = listOf("Jerking the weight"),
            easier = listOf("db_row_one_arm"),
        ),
        Exercise(
            "reverse_lunge", "Reverse Lunge", listOf("Quads", "Glutes"), LUNGE,
            emptySet(), LoadKind.BODYWEIGHT, 1, perSide = true, isCompound = true, start = p(2, 8, 12, 60),
            instructions = listOf("Stand tall.", "Step one foot back and lower the back knee towards the floor.", "Push through the front foot to return."),
            cues = listOf("Front knee stays over the foot"), commonErrors = listOf("Short steps", "Wobbling"),
            easier = listOf("split_squat"), harder = listOf("db_reverse_lunge"),
            safetyNotes = listOf("Hold a wall or chair for balance if needed."),
            aliases = listOf("lunge", "lunges"),
        ),
        Exercise(
            "db_reverse_lunge", "Dumbbell Reverse Lunge", listOf("Quads", "Glutes"), LUNGE,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 3, perSide = true, isCompound = true, start = p(3, 6, 10, 90),
            instructions = listOf("Hold dumbbells at your sides.", "Step back and lower the back knee.", "Return to standing."),
            cues = listOf("Stay tall"), commonErrors = listOf("Losing balance"),
            easier = listOf("reverse_lunge"),
        ),
        Exercise(
            "split_squat", "Split Squat", listOf("Quads", "Glutes"), LUNGE,
            emptySet(), LoadKind.BODYWEIGHT, 1, perSide = true, isCompound = true, start = p(2, 8, 12, 60),
            instructions = listOf("Stand in a long staggered stance.", "Lower straight down until the back knee nearly touches the floor.", "Stand up. Finish one side, switch."),
            cues = listOf("Torso upright"), commonErrors = listOf("Front heel lifting"),
            harder = listOf("db_split_squat"),
            aliases = listOf("split squat"),
        ),
        Exercise(
            "db_split_squat", "Dumbbell Split Squat", listOf("Quads", "Glutes"), LUNGE,
            setOf(DUMBBELL_PAIR), LoadKind.DUMBBELL_PAIR, 2, perSide = true, isCompound = true, start = p(3, 6, 10, 90),
            instructions = listOf("Hold dumbbells at your sides in a staggered stance.", "Lower straight down.", "Stand up."),
            cues = listOf("Control the descent"), commonErrors = listOf("Leaning forward"),
            easier = listOf("split_squat"),
        ),
        Exercise(
            "standing_calf_raise", "Standing Calf Raise", listOf("Calves"), CALF,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = false, start = p(2, 12, 20, 45),
            instructions = listOf("Stand near a wall for balance (a step edge adds range).", "Rise onto the balls of your feet.", "Pause, lower slowly."),
            cues = listOf("Full range, pause at the top"), commonErrors = listOf("Bouncing"),
            harder = listOf("db_single_leg_calf_raise"),
            aliases = listOf("calf raise"),
        ),
        Exercise(
            "db_single_leg_calf_raise", "Single-Leg Dumbbell Calf Raise", listOf("Calves"), CALF,
            setOf(DUMBBELL), LoadKind.DUMBBELL_SINGLE, 2, perSide = true, isCompound = false, start = p(2, 10, 15, 45),
            instructions = listOf("Hold a dumbbell in one hand, the other on a wall.", "Raise up on one foot.", "Lower slowly."),
            cues = listOf("Pause at the top"), commonErrors = listOf("Rushing"),
            easier = listOf("standing_calf_raise"),
        ),
        Exercise(
            "ankle_weight_glute_kickback", "Ankle-Weight Glute Kickback", listOf("Glutes"), HINGE,
            setOf(ANKLE_WEIGHT), LoadKind.ANKLE_WEIGHT, 1, perSide = true, isCompound = false, start = p(2, 12, 15, 45),
            instructions = listOf("On hands and knees with ankle weights on.", "Push one heel back and up.", "Lower with control."),
            cues = listOf("Don't arch the lower back"), commonErrors = listOf("Swinging"),
        ),
        Exercise(
            "kb_swing", "Kettlebell Swing", listOf("Glutes", "Hamstrings"), HINGE,
            setOf(KETTLEBELL), LoadKind.KETTLEBELL, 3, isCompound = true, start = p(3, 10, 15, 90),
            instructions = listOf("Hike the bell back between your legs.", "Snap hips forward to float it to chest height.", "Let it fall back into the hinge."),
            cues = listOf("Hips, not arms"), commonErrors = listOf("Squatting the swing"),
            easier = listOf("kb_deadlift"),
            safetyNotes = listOf("Learn the hinge first; clear space around you."),
        ),
        // ---- Core
        Exercise(
            "dead_bug", "Dead Bug", listOf("Core"), CORE,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = false, start = p(2, 6, 10, 45),
            instructions = listOf("Lie on your back, arms up, knees at 90°.", "Lower opposite arm and leg slowly.", "Return and switch sides (each side = 1 rep)."),
            cues = listOf("Lower back stays on the floor"), commonErrors = listOf("Back arching", "Moving too fast"),
            harder = listOf("plank"),
        ),
        Exercise(
            "plank", "Plank", listOf("Core"), CORE,
            emptySet(), LoadKind.BODYWEIGHT, 1, isCompound = false, start = t(2, 20, 45),
            instructions = listOf("Forearms on the floor, body in a straight line.", "Hold, breathing normally."),
            cues = listOf("Squeeze glutes"), commonErrors = listOf("Hips sagging or piking"),
            easier = listOf("dead_bug"),
        ),
        Exercise(
            "side_plank", "Side Plank", listOf("Obliques"), CORE,
            emptySet(), LoadKind.BODYWEIGHT, 2, perSide = true, isCompound = false, start = t(2, 15, 45),
            instructions = listOf("Lie on your side on one forearm.", "Lift hips into a straight line.", "Hold, then switch sides."),
            cues = listOf("Hips stacked"), commonErrors = listOf("Hips dropping"),
            easier = listOf("plank"),
        ),
        Exercise(
            "russian_twist", "Russian Twist", listOf("Obliques", "Core"), CORE,
            emptySet(), LoadKind.BODYWEIGHT, 2, isCompound = false, start = p(2, 10, 16, 45),
            instructions = listOf("Sit with knees bent, lean back slightly with a straight back.", "Rotate your torso side to side (each side = 1 rep).", "Feet can stay on the floor."),
            cues = listOf("Rotate from the ribs, slow tempo"), commonErrors = listOf("Rounding the lower back", "Just moving the arms"),
            easier = listOf("dead_bug"),
            safetyNotes = listOf("Skip if you have lower-back discomfort; use dead bugs instead. Holding a 17.5 kg dumbbell here is not recommended in the comeback phase."),
            aliases = listOf("twist", "russian twists"),
        ),
        Exercise(
            "hanging_knee_raise", "Hanging Knee Raise", listOf("Core", "Grip"), CORE,
            setOf(PULL_UP_BAR), LoadKind.BODYWEIGHT, 2, isCompound = false, start = p(2, 6, 12, 60),
            instructions = listOf("Hang from the bar.", "Raise knees towards your chest without swinging.", "Lower slowly."),
            cues = listOf("No swinging"), commonErrors = listOf("Using momentum"),
            easier = listOf("dead_bug"),
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Exercise? = byId[id]

    /** Finds an exercise mentioned in free text by name or alias (longest match wins). */
    fun findMentioned(text: String): Exercise? {
        val t = text.lowercase()
        return all.flatMap { ex -> (ex.aliases + ex.name.lowercase()).map { it to ex } }
            .filter { (alias, _) -> t.contains(alias) }
            .maxByOrNull { it.first.length }?.second
    }
}
