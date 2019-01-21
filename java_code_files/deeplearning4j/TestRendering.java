/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.deeplearning4j.ui;

import org.apache.commons.io.FileUtils;
import org.deeplearning4j.ui.api.Component;
import org.deeplearning4j.ui.api.LengthUnit;
import org.deeplearning4j.ui.api.Style;
import org.deeplearning4j.ui.components.chart.*;
import org.deeplearning4j.ui.components.chart.style.StyleChart;
import org.deeplearning4j.ui.components.component.ComponentDiv;
import org.deeplearning4j.ui.components.component.style.StyleDiv;
import org.deeplearning4j.ui.components.decorator.DecoratorAccordion;
import org.deeplearning4j.ui.components.decorator.style.StyleAccordion;
import org.deeplearning4j.ui.components.table.ComponentTable;
import org.deeplearning4j.ui.components.table.style.StyleTable;
import org.deeplearning4j.ui.components.text.ComponentText;
import org.deeplearning4j.ui.components.text.style.StyleText;
import org.junit.Ignore;
import org.junit.Test;
import org.nd4j.shade.jackson.databind.ObjectMapper;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * This test: generated a HTML file that you can open to view some example graphs.
 * The generated HTML file should appear in the deeplearning4j-ui-components directory (TestRendering.html)
 * *** NOTE: Open this in IntelliJ: Right click on file -> Open In Browser ***
 */
public class TestRendering {

    @Ignore
    @Test
    public void test() throws Exception {

        List<Component> list = new ArrayList<>();

        //Common style for all of the charts
        StyleChart s = new StyleChart.Builder().width(640, LengthUnit.Px).height(480, LengthUnit.Px)
                        .margin(LengthUnit.Px, 100, 40, 40, 20).strokeWidth(2)
                        .pointSize(4).seriesColors(Color.GREEN, Color.MAGENTA).titleStyle(new StyleText.Builder()
                                        .font("courier").fontSize(16).underline(true).color(Color.GRAY).build())
                        .build();

        //Line chart with vertical grid
        Component c1 = new ChartLine.Builder("Line Chart!", s)
                        .addSeries("series0", new double[] {0, 1, 2, 3}, new double[] {0, 2, 1, 4})
                        .addSeries("series1", new double[] {0, 1, 2, 3}, new double[] {0, 1, 0.5, 2.5})
                        .setGridWidth(1.0, null) //Vertical grid lines, no horizontal grid
                        .build();
        list.add(c1);

        //Scatter chart
        Component c2 = new ChartScatter.Builder("Scatter!", s)
                        .addSeries("series0", new double[] {0, 1, 2, 3}, new double[] {0, 2, 1, 4}).showLegend(true)
                        .setGridWidth(0, 0).build();
        list.add(c2);

        //Histogram with variable sized bins
        Component c3 = new ChartHistogram.Builder("Histogram!", s).addBin(-1, -0.5, 0.2).addBin(-0.5, 0, 0.5)
                        .addBin(0, 1, 2.5).addBin(1, 2, 0.5).build();
        list.add(c3);

        //Stacked area chart
        Component c4 = new ChartStackedArea.Builder("Area Chart!", s).setXValues(new double[] {0, 1, 2, 3, 4, 5})
                        .addSeries("series0", new double[] {0, 1, 0, 2, 0, 1})
                        .addSeries("series1", new double[] {2, 1, 2, 0.5, 2, 1}).build();
        list.add(c4);

        //Table
        StyleTable ts = new StyleTable.Builder().backgroundColor(Color.LIGHT_GRAY).headerColor(Color.ORANGE)
                        .borderWidth(1).columnWidths(LengthUnit.Percent, 20, 40, 40).width(500, LengthUnit.Px)
                        .height(200, LengthUnit.Px).build();

        Component c5 = new ComponentTable.Builder(ts).header("H1", "H2", "H3").content(
                        new String[][] {{"row0col0", "row0col1", "row0col2"}, {"row1col0", "row1col1", "row1col2"}})
                        .build();
        list.add(c5);

        //Accordion decorator, with the same chart
        StyleAccordion ac = new StyleAccordion.Builder().height(480, LengthUnit.Px).width(640, LengthUnit.Px).build();

        Component c6 = new DecoratorAccordion.Builder(ac).title("Accordion - Collapsed By Default!")
                        .setDefaultCollapsed(true).addComponents(c5).build();
        list.add(c6);

        //Text with styling
        Component c7 = new ComponentText.Builder("Here's some blue text in a green div!",
                        new StyleText.Builder().font("courier").fontSize(30).underline(true).color(Color.BLUE).build())
                                        .build();

        //Div, with a chart inside
        Style divStyle = new StyleDiv.Builder().width(30, LengthUnit.Percent).height(200, LengthUnit.Px)
                        .backgroundColor(Color.GREEN).floatValue(StyleDiv.FloatValue.right).build();
        Component c8 = new ComponentDiv(divStyle, c7,
                        new ComponentText("(Also: it's float right, 30% width, 200 px high )", null));
        list.add(c8);


        //Timeline chart:
        List<ChartTimeline.TimelineEntry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(new ChartTimeline.TimelineEntry("e0-" + i, 10 * i, 10 * i + 5, Color.BLUE));
        }
        List<ChartTimeline.TimelineEntry> entries2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries2.add(new ChartTimeline.TimelineEntry("e1-" + i, (int) (5 * i + 0.2 * i * i),
                            (int) (5 * i + 0.2 * i * i) + 3, Color.ORANGE));
        }
        List<ChartTimeline.TimelineEntry> entries3 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries3.add(new ChartTimeline.TimelineEntry("e2-" + i, (int) (2 * i + 0.6 * i * i + 3),
                            (int) (2 * i + 0.6 * i * i + 3) + 2 * i + 1));
        }
        Color[] c = new Color[] {Color.CYAN, Color.YELLOW, Color.GREEN, Color.PINK};
        List<ChartTimeline.TimelineEntry> entries4 = new ArrayList<>();
        Random r = new Random(12345);
        for (int i = 0; i < 10; i++) {
            entries4.add(new ChartTimeline.TimelineEntry("e3-" + i, (int) (2 * i + 0.6 * i * i + 3),
                            (int) (2 * i + 0.6 * i * i + 3) + i + 1, c[r.nextInt(c.length)]));
        }
        Component c9 = new ChartTimeline.Builder("Title", s).addLane("Lane 0", entries).addLane("Lane 1", entries2)
                        .addLane("Lane 2", entries3).addLane("Lane 3", entries4).build();
        list.add(c9);



        //Generate HTML
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n" + "<html lang=\"en\">\n" + "<head>\n" + "    <meta charset=\"UTF-8\">\n"
                        + "    <title>Title</title>\n" + "</head>\n" + "<body>\n" + "\n" + "    <div id=\"maindiv\">\n"
                        + "\n" + "    </div>\n" + "\n" + "\n"
                        + "    <script src=\"//code.jquery.com/jquery-1.10.2.js\"></script>\n"
                        + "    <script src=\"https://code.jquery.com/ui/1.11.4/jquery-ui.min.js\"></script>\n"
                        + "    <link rel=\"stylesheet\" href=\"//code.jquery.com/ui/1.11.4/themes/smoothness/jquery-ui.css\">\n"
                        + "    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/d3/3.5.5/d3.min.js\"></script>\n"
                        + "    <script src=\"src/main/resources/assets/dl4j-ui.js\"></script>\n" + "\n"
                        + "    <script>\n");

        ObjectMapper om = new ObjectMapper();
        for (int i = 0; i < list.size(); i++) {
            Component component = list.get(i);
            sb.append("        ").append("var str").append(i).append(" = '")
                            .append(om.writeValueAsString(component).replaceAll("'", "\\\\'")).append("';\n");

            sb.append("        ").append("var obj").append(i).append(" = Component.getComponent(str").append(i)
                            .append(");\n");
            sb.append("        ").append("obj").append(i).append(".render($('#maindiv'));\n");
            sb.append("\n\n");
        }


        sb.append("    </script>\n" + "\n" + "</body>\n" + "</html>");

        FileUtils.writeStringToFile(new File("TestRendering.html"), sb.toString());
    }

}
