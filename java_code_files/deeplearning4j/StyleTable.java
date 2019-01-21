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

package org.deeplearning4j.ui.components.table.style;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.deeplearning4j.ui.api.LengthUnit;
import org.deeplearning4j.ui.api.Style;
import org.deeplearning4j.ui.api.Utils;
import org.nd4j.shade.jackson.annotation.JsonInclude;

import java.awt.*;

/**
 * Created by Alex on 3/04/2016.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StyleTable extends Style {

    private double[] columnWidths;
    private LengthUnit columnWidthUnit;
    private Integer borderWidthPx;
    private String headerColor;
    private String backgroundColor;
    private String whitespaceMode;


    private StyleTable(Builder builder) {
        super(builder);
        this.columnWidths = builder.columnWidths;
        this.columnWidthUnit = builder.columnWidthUnit;
        this.borderWidthPx = builder.borderWidthPx;
        this.headerColor = builder.headerColor;
        this.backgroundColor = builder.backgroundColor;
        this.whitespaceMode = builder.whitespaceMode;
    }

    //No arg constructor for Jackson
    private StyleTable() {

    }


    public static class Builder extends Style.Builder<Builder> {

        private double[] columnWidths;
        private LengthUnit columnWidthUnit;
        private Integer borderWidthPx;
        private String headerColor;
        private String backgroundColor;
        private String whitespaceMode;

        /**
         * Specify the widths for the columns
         *
         * @param unit   Unit that the widths are specified in
         * @param widths Width values for the columns
         */
        public Builder columnWidths(LengthUnit unit, double... widths) {
            this.columnWidthUnit = unit;
            this.columnWidths = widths;
            return this;
        }

        /**
         * @param borderWidthPx    Width of the border, in px
         */
        public Builder borderWidth(int borderWidthPx) {
            this.borderWidthPx = borderWidthPx;
            return this;
        }

        /**
         * @param color    Background color for the header row
         */
        public Builder headerColor(Color color) {
            String hex = Utils.colorToHex(color);
            return headerColor(hex);
        }

        /**
         * @param color    Background color for the header row
         */
        public Builder headerColor(String color) {
            if (!color.toLowerCase().matches("#[a-f0-9]{6}"))
                throw new IllegalArgumentException("Invalid color: must be hex format. Got: " + color);
            this.headerColor = color;
            return this;
        }

        /**
         * @param color    Background color for the table cells (ex. header row)
         */
        public Builder backgroundColor(Color color) {
            String hex = Utils.colorToHex(color);
            return backgroundColor(hex);
        }

        /**
         * @param color    Background color for the table cells (ex. header row)
         */
        public Builder backgroundColor(String color) {
            if (!color.toLowerCase().matches("#[a-f0-9]{6}"))
                throw new IllegalArgumentException("Invalid color: must be hex format. Got: " + color);
            this.backgroundColor = color;
            return this;
        }

        /**
         * Set the whitespace mode (CSS style tag). For example, "pre" to maintain current formatting with no wrapping,
         * "pre-wrap" to wrap (but otherwise take into account new line characters in text, etc)
         *
         * @param whitespaceMode    CSS whitespace mode
         */
        public Builder whitespaceMode(String whitespaceMode) {
            this.whitespaceMode = whitespaceMode;
            return this;
        }

        public StyleTable build() {
            return new StyleTable(this);
        }
    }

}
