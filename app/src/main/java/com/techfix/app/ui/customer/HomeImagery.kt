package com.techfix.app.ui.customer

import androidx.annotation.DrawableRes
import com.techfix.app.R

/**
 * The photography bundled with the app.
 *
 * These are local drawables on purpose, not a live image API: the Home screen
 * has to render identically offline and in the demo video, and an
 * unauthenticated call to a photo service would be both a new network
 * dependency and a source that can change under us between runs.
 *
 * All five are from Pexels under the Pexels License (free to use, commercial
 * use allowed, attribution not required — credited anyway for the report's
 * references section):
 *
 * - [hero] `img_repair_bench.jpg` — "Smartphone repair tools on a workbench",
 *   Fotografia Lui Vlad.
 *   https://www.pexels.com/photo/smartphone-repair-tools-on-a-workbench-31862953/
 * - [trackRepair] `img_track_repair.jpg` — "A hand fixing an electronic device
 *   using screwdriver", Tima Miroshnichenko.
 *   https://www.pexels.com/photo/a-hand-fixing-an-electronic-device-using-screwdriver-6755075/
 * - [repairHistory] `img_repair_history.jpg` — "Close up of man repairing a
 *   computer", IT services EU.
 *   https://www.pexels.com/photo/close-up-of-man-repairing-a-computer-7639374/
 * - [noRepairs] `img_no_repairs.jpg` — "Cracked screen of a smartphone",
 *   Towfiqu barbhuiya.
 *   https://www.pexels.com/photo/cracked-screen-of-a-smartphone-11921157/
 * - [technicianAtWork] `img_technician_laptop.jpg` — "Technician repairing
 *   laptop's internal components", Jobelle Meana.
 *   https://www.pexels.com/photo/technician-repairing-laptop-s-internal-components-37489058/
 *
 * They live in `res/drawable-nodpi` because they are photographs, not icons:
 * one asset scaled by the layout, rather than five density buckets of the
 * same picture. Each is downloaded pre-compressed at the width it is actually
 * drawn at (500–1000px), so all five together are ~230KB.
 */
object HomeImagery {
    @DrawableRes
    val hero: Int = R.drawable.img_repair_bench

    @DrawableRes
    val trackRepair: Int = R.drawable.img_track_repair

    @DrawableRes
    val repairHistory: Int = R.drawable.img_repair_history

    @DrawableRes
    val noRepairs: Int = R.drawable.img_no_repairs

    @DrawableRes
    val technicianAtWork: Int = R.drawable.img_technician_laptop
}
