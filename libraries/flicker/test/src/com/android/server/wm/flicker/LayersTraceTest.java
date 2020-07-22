/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wm.flicker;

import static com.android.server.wm.flicker.TestFileUtils.readTestFile;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.view.WindowManager;

import androidx.test.filters.FlakyTest;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains {@link LayersTrace} tests. To run this test: {@code atest
 * FlickerLibTest:LayersTraceTest}
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LayersTraceTest {
    private static LayersTrace readLayerTraceFromFile(String relativePath) {
        try {
            return LayersTrace.parseFrom(readTestFile(relativePath));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Region getDisplayBounds() {
        Point display = new Point();
        WindowManager wm =
                (WindowManager)
                        InstrumentationRegistry.getContext()
                                .getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealSize(display);
        return new Region(new Rect(0, 0, display.x, display.y));
    }

    @Test
    public void canParseAllLayers() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        assertThat(trace.getEntries()).isNotEmpty();
        assertThat(trace.getEntries().get(0).getTimestamp()).isEqualTo(2307984557311L);
        assertThat(trace.getEntries().get(trace.getEntries().size() - 1).getTimestamp())
                .isEqualTo(2308521813510L);
        List<Layer> flattenedLayers = trace.getEntries().get(0).getFlattenedLayers();
        String msg =
                "Layers:\n"
                        + flattenedLayers
                                .stream()
                                .map(layer -> layer.getProto().name)
                                .collect(Collectors.joining("\n\t"));
        assertWithMessage(msg).that(flattenedLayers).hasSize(47);
    }

    @FlakyTest
    @Test
    public void canParseVisibleLayers() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        assertThat(trace.getEntries()).isNotEmpty();
        assertThat(trace.getEntries().get(0).getTimestamp()).isEqualTo(2307984557311L);
        assertThat(trace.getEntries().get(trace.getEntries().size() - 1).getTimestamp())
                .isEqualTo(2308521813510L);
        List<Layer> flattenedLayers = trace.getEntries().get(0).getFlattenedLayers();
        List<Layer> visibleLayers =
                flattenedLayers
                        .stream()
                        .filter(layer -> layer.isVisible() && !layer.isHiddenByParent())
                        .collect(Collectors.toList());

        String msg =
                "Visible Layers:\n"
                        + visibleLayers
                                .stream()
                                .map(layer -> "\t" + layer.getProto().name)
                                .collect(Collectors.joining("\n"));

        assertWithMessage(msg).that(visibleLayers).hasSize(9);
    }

    @Test
    public void canParseLayerHierarchy() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        assertThat(trace.getEntries()).isNotEmpty();
        assertThat(trace.getEntries().get(0).getTimestamp()).isEqualTo(2307984557311L);
        assertThat(trace.getEntries().get(trace.getEntries().size() - 1).getTimestamp())
                .isEqualTo(2308521813510L);
        List<Layer> layers = trace.getEntries().get(0).getRootLayers();
        assertThat(layers).hasSize(2);
        assertThat(layers.get(0).getChildren()).hasSize(layers.get(0).getProto().children.length);
        assertThat(layers.get(1).getChildren()).hasSize(layers.get(1).getProto().children.length);
    }

    // b/76099859
    @Test
    public void canDetectOrphanLayers() {
        try {
            readLayerTraceFromFile("layers_trace_orphanlayers.pb");
            fail("Failed to detect orphaned layers.");
        } catch (RuntimeException exception) {
            assertThat(exception.getMessage())
                    .contains(
                            "Failed to parse layers trace. Found orphan layers "
                                    + "with parent layer id:1006 : 49");
        }
    }

    // b/75276931
    @Test
    public void canDetectUncoveredRegion() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        LayerTraceEntry entry = trace.getEntry(2308008331271L);

        AssertionResult result = entry.coversRegion(new Region(0, 0, 1440, 2960));

        assertThat(result.failed()).isTrue();
        assertThat(result.getReason()).contains("Region to test: SkRegion((0,0,1440,2960))");
        assertThat(result.getReason()).contains("first empty point: 0, 99");
        assertThat(result.getReason()).contains("visible regions:");
        assertWithMessage("Reason contains list of visible regions")
                .that(result.getReason())
                .contains("StatusBar#0SkRegion((0,0,1440,98))");
    }

    // Visible region tests
    @Test
    public void canTestLayerVisibleRegion_layerDoesNotExist() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        LayerTraceEntry entry = trace.getEntry(2308008331271L);

        final Region expectedVisibleRegion = new Region(0, 0, 1, 1);
        AssertionResult result = entry.hasVisibleRegion("ImaginaryLayer", expectedVisibleRegion);

        assertThat(result.failed()).isTrue();
        assertThat(result.getReason()).contains("Could not find ImaginaryLayer");
    }

    @Test
    public void canTestLayerVisibleRegion_layerDoesNotHaveExpectedVisibleRegion() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        LayerTraceEntry entry = trace.getEntry(2307993020072L);

        final Region expectedVisibleRegion = new Region(0, 0, 1, 1);
        AssertionResult result =
                entry.hasVisibleRegion("NexusLauncherActivity#2", expectedVisibleRegion);

        assertThat(result.failed()).isTrue();
        assertThat(result.getReason())
                .contains(
                        "Layer com.google.android.apps.nexuslauncher/com.google.android.apps"
                                + ".nexuslauncher.NexusLauncherActivity#2 is invisible: activeBuffer=null"
                                + " type != ColorLayer flags=1 (FLAG_HIDDEN set) visible region is empty");
    }

    @Test
    public void canTestLayerVisibleRegion_layerIsHiddenByParent() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        LayerTraceEntry entry = trace.getEntry(2308455948035L);

        final Region expectedVisibleRegion = new Region(0, 0, 1, 1);
        AssertionResult result =
                entry.hasVisibleRegion(
                        "SurfaceView - com.android.chrome/com.google.android.apps.chrome.Main",
                        expectedVisibleRegion);

        assertThat(result.failed()).isTrue();
        assertThat(result.getReason())
                .contains(
                        "Layer SurfaceView - com.android.chrome/com.google.android.apps.chrome.Main#0 is "
                                + "hidden by parent: com.android.chrome/com.google.android.apps.chrome"
                                + ".Main#0");
    }

    @Test
    public void canTestLayerVisibleRegion_incorrectRegionSize() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        LayerTraceEntry entry = trace.getEntry(2308008331271L);

        final Region expectedVisibleRegion = new Region(0, 0, 1440, 99);
        AssertionResult result = entry.hasVisibleRegion("StatusBar", expectedVisibleRegion);

        assertThat(result.failed()).isTrue();
        assertThat(result.getReason())
                .contains(
                        "StatusBar#0 has visible "
                                + "region:SkRegion((0,0,1440,98)) expected:SkRegion((0,0,1440,99))");
    }

    @Test
    public void canTestLayerVisibleRegion() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_emptyregion.pb");
        LayerTraceEntry entry = trace.getEntry(2308008331271L);

        final Region expectedVisibleRegion = new Region(0, 0, 1440, 98);
        AssertionResult result = entry.hasVisibleRegion("StatusBar", expectedVisibleRegion);

        assertThat(result.passed()).isTrue();
    }

    @Test
    public void canTestLayerVisibleRegion_layerIsNotVisible() {
        LayersTrace trace = readLayerTraceFromFile("layers_trace_invalid_layer_visibility.pb");
        LayerTraceEntry entry = trace.getEntry(252794268378458L);

        AssertionResult result = entry.isVisible("com.android.server.wm.flicker.testapp");
        assertThat(result.failed()).isTrue();
        assertThat(result.getReason())
                .contains(
                        "Layer com.android.server.wm.flicker.testapp/com.android.server.wm.flicker"
                                + ".testapp.SimpleActivity#0 is invisible: type != ColorLayer visible "
                                + "region is empty");
    }
}
