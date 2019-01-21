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

package org.deeplearning4j.text.documentiterator;

import lombok.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * This LabelAwareIterator scans folder for files, and returns them as LabelledDocuments.
 * Each LabelledDocument will set it's Label to file name.
 *
 * @author raver119@gmail.com
 */
public class FilenamesLabelAwareIterator implements LabelAwareIterator {
    protected List<File> files;
    protected AtomicInteger position = new AtomicInteger(0);
    protected LabelsSource labelsSource;
    protected boolean absPath = false;

    /*
        Please keep this method protected, it's used in tests
     */
    protected FilenamesLabelAwareIterator() {

    }

    protected FilenamesLabelAwareIterator(@NonNull List<File> files, @NonNull LabelsSource source) {
        this.files = files;
        this.labelsSource = source;
    }

    @Override
    public boolean hasNextDocument() {
        return position.get() < files.size();
    }


    @Override
    public LabelledDocument nextDocument() {
        File fileToRead = files.get(position.getAndIncrement());
        String label = (absPath) ? fileToRead.getAbsolutePath() : fileToRead.getName();
        labelsSource.storeLabel(label);
        try {
            LabelledDocument document = new LabelledDocument();
            BufferedReader reader = new BufferedReader(new FileReader(fileToRead));
            StringBuilder builder = new StringBuilder();
            String line = "";
            while ((line = reader.readLine()) != null)
                builder.append(line).append(" ");

            document.setContent(builder.toString());
            document.addLabel(label);

            try {
                reader.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return document;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasNext() {
        return hasNextDocument();
    }

    @Override
    public LabelledDocument next() {
        return nextDocument();
    }

    @Override
    public void remove() {
        // no-op
    }

    @Override
    public void shutdown() {
        // no-op
    }

    @Override
    public void reset() {
        position.set(0);
    }

    @Override
    public LabelsSource getLabelsSource() {
        return labelsSource;
    }

    public static class Builder {
        protected List<File> foldersToScan = new ArrayList<>();

        private List<File> fileList = new ArrayList<>();
        private List<String> labels = new ArrayList<>();
        private boolean absPath = false;

        public Builder() {

        }

        /**
         * Root folder for labels -> documents.
         * Each subfolder name will be presented as label, and contents of this folder will be represented as LabelledDocument, with label attached
         *
         * @param folder folder to be scanned for labels and files
         * @return
         */
        public Builder addSourceFolder(@NonNull File folder) {
            foldersToScan.add(folder);
            return this;
        }

        public Builder useAbsolutePathAsLabel(boolean reallyUse) {
            this.absPath = reallyUse;
            return this;
        }

        private void scanFolder(File folderToScan) {
            File[] files = folderToScan.listFiles();
            if (files == null || files.length == 0)
                return;


            for (File fileLabel : files) {
                if (fileLabel.isDirectory()) {
                    scanFolder(fileLabel);
                } else {
                    fileList.add(fileLabel);
                }
            }
        }

        public FilenamesLabelAwareIterator build() {
            // search for all files in all folders provided


            for (File file : foldersToScan) {
                if (!file.isDirectory())
                    continue;
                scanFolder(file);
            }

            LabelsSource source = new LabelsSource(labels);
            FilenamesLabelAwareIterator iterator = new FilenamesLabelAwareIterator(fileList, source);
            iterator.absPath = this.absPath;

            return iterator;
        }
    }
}
