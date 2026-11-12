package com.mathis.mandala.app

import one.tearoom.review.AppReview
import one.tearoom.review.ReviewConfig

/**
 * The rating prompt through tearoom-android-kit (kit ADR-0006): Google Play's review dialog once Dahlia
 * was opened on 10 different days over at least 7, at most once per version, while the editor is calm;
 * the menu's Dahlia page has the permanent row. The Play id is fixed, so the debug, fast and free builds
 * open the real listing too. Local builds force or reset it through adb (ReviewDebugReceiver in
 * src/localBuild/AndroidManifest.xml).
 */
val Review = AppReview(ReviewConfig(playPackage = "one.tearoom.mandala"))
