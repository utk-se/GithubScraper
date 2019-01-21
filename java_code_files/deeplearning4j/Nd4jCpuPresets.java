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

package org.nd4j.nativeblas;

import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;
import org.bytedeco.javacpp.tools.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;

/**
 *
 * @author saudet
 */
@Properties(target = "org.nd4j.nativeblas.Nd4jCpu",
                value = {@Platform(define = "LIBND4J_ALL_OPS", include = {
                                              "array/DataType.h",
                                              "Environment.h",
                                              "types/utf8string.h",
                                              "NativeOps.h",
                                              "memory/ExternalWorkspace.h",
                                              "memory/Workspace.h",
                                              "indexing/NDIndex.h",
                                              "indexing/IndicesList.h",
                                              "graph/VariableType.h",
                                              "graph/ArgumentsList.h",
                                              "types/pair.h",
                                              "NDArray.h",
                                              "array/NDArrayList.h",
                                              "array/ResultSet.h",
                                              "types/pair.h",
                                              "graph/RandomGenerator.h",
                                              "graph/Variable.h",
                                              "graph/VariablesSet.h",
                                              "graph/FlowPath.h",
                                              "graph/Intervals.h",
                                              "graph/Stash.h",
                                              "graph/GraphState.h",
                                              "graph/VariableSpace.h",
                                              "helpers/helper_generator.h",
                                              "graph/profiling/GraphProfile.h",
                                              "graph/profiling/NodeProfile.h",
                                              "graph/Context.h",
                                              "graph/ContextPrototype.h",
                                              "graph/ResultWrapper.h",
                                              "helpers/shape.h",
                                              "helpers/OpArgsHolder.h",
                                              "array/ShapeList.h",
                                              "type_boilerplate.h",
                                              "op_boilerplate.h",
                                              //"enum_boilerplate.h",
                                              //"op_enums.h",
                                              "ops/InputType.h",
                                              "ops/declarable/OpDescriptor.h",
                                              "ops/declarable/BroadcastableOp.h",                                              
                                              "ops/declarable/DeclarableOp.h",
                                              "ops/declarable/DeclarableListOp.h",
                                              "ops/declarable/DeclarableReductionOp.h",
                                              "ops/declarable/DeclarableCustomOp.h",
                                              "ops/declarable/BooleanOp.h",
                                              "ops/declarable/LogicOp.h",
                                              "ops/declarable/OpRegistrator.h",
                                              "ops/declarable/CustomOperations.h",
                                              "ops/declarable/headers/activations.h",
                                              "ops/declarable/headers/boolean.h",
                                              "ops/declarable/headers/broadcastable.h",
                                              "ops/declarable/headers/convo.h",
                                              "ops/declarable/headers/list.h",
                                              "ops/declarable/headers/recurrent.h",
                                              "ops/declarable/headers/transforms.h",
                                              "ops/declarable/headers/parity_ops.h",
                                              "ops/declarable/headers/shape.h",
                                              "ops/declarable/headers/random.h",
                                              "ops/declarable/headers/nn.h",
                                              "ops/declarable/headers/blas.h",
                                              "ops/declarable/headers/tests.h",
                                              "ops/declarable/headers/bitwise.h",
                                              "ops/declarable/headers/loss.h",
                                              "ops/declarable/headers/datatypes.h",
                                              "ops/declarable/headers/third_party.h"},
                                   exclude = {"ops/declarable/headers/activations.h",
                                              "ops/declarable/headers/boolean.h",
                                              "ops/declarable/headers/broadcastable.h",
                                              "ops/declarable/headers/convo.h",
                                              "ops/declarable/headers/list.h",
                                              "ops/declarable/headers/recurrent.h",
                                              "ops/declarable/headers/transforms.h",
                                              "ops/declarable/headers/parity_ops.h",
                                              "ops/declarable/headers/shape.h",
                                              "ops/declarable/headers/random.h",
                                              "ops/declarable/headers/nn.h",
                                              "ops/declarable/headers/blas.h",
                                              "ops/declarable/headers/bitwise.h",
                                              "ops/declarable/headers/tests.h",
                                              "ops/declarable/headers/loss.h",
                                              "ops/declarable/headers/datatypes.h",
                                              "ops/declarable/headers/third_party.h",
                                              "cnpy/cnpy.h"
                                   },
                                compiler = {"cpp11", "nowarnings"}, library = "jnind4jcpu", link = "nd4jcpu",
                                preloadresource = "org/bytedeco/javacpp/", preload = {"openblas", "openblas_nolapack", "libnd4jcpu"}),
                                @Platform(value = "linux", preload = {"gomp@.1", "iomp5", "mklml_intel", "mkldnn@.0"},
                                                preloadpath = {"/lib64/", "/lib/", "/usr/lib64/", "/usr/lib/",
                                                                "/usr/lib/powerpc64-linux-gnu/",
                                                                "/usr/lib/powerpc64le-linux-gnu/"}),
                @Platform(value = {"linux-arm", "linux-ppc"},
                                preload = {"gomp@.1", "gcc_s@.1", "quadmath@.0", "gfortran@.3", "openblas@.0", "libnd4jcpu"}),
                @Platform(value = "macosx", preload = {"gcc_s@.1", "gomp@.1", "stdc++@.6", "iomp5", "mklml", "mkldnn@.0"},
                                preloadpath = {"/usr/local/lib/gcc/8/", "/usr/local/lib/gcc/7/", "/usr/local/lib/gcc/6/", "/usr/local/lib/gcc/5/"}),
                @Platform(value = "windows", preload = {"libwinpthread-1", "libgcc_s_seh-1", "libgomp-1", "libstdc++-6",
                                                        "msvcr120", "libiomp5md", "mklml", "libmkldnn", "libnd4jcpu"}),
                @Platform(extension = {"-avx512", "-avx2"}) })
public class Nd4jCpuPresets implements InfoMapper, BuildEnabled {

    private Logger logger;
    private java.util.Properties properties;
    private String encoding;

    @Override
    public void init(Logger logger, java.util.Properties properties, String encoding) {
        this.logger = logger;
        this.properties = properties;
        this.encoding = encoding;
    }

    @Override
    public void map(InfoMap infoMap) {
        infoMap.put(new Info("thread_local", "ND4J_EXPORT", "INLINEDEF", "CUBLASWINAPI", "FORCEINLINE",
                             "_CUDA_H", "_CUDA_D", "_CUDA_G", "_CUDA_HD", "LIBND4J_ALL_OPS", "NOT_EXCLUDED").cppTypes().annotations())
                        .put(new Info("NativeOps").base("org.nd4j.nativeblas.NativeOps"))
                        .put(new Info("const char").valueTypes("byte").pointerTypes("@Cast(\"char*\") String",
                                        "@Cast(\"char*\") BytePointer"))
                        .put(new Info("char").valueTypes("char").pointerTypes("@Cast(\"char*\") BytePointer",
                                        "@Cast(\"char*\") String"))
                        .put(new Info("Nd4jPointer").cast().valueTypes("Pointer").pointerTypes("PointerPointer"))
                        .put(new Info("Nd4jLong").cast().valueTypes("long").pointerTypes("LongPointer", "LongBuffer",
                                        "long[]"))
                        .put(new Info("Nd4jStatus").cast().valueTypes("int").pointerTypes("IntPointer", "IntBuffer",
                                        "int[]"))
                        .put(new Info("float16").cast().valueTypes("short").pointerTypes("ShortPointer", "ShortBuffer",
                                        "short[]"))
                        .put(new Info("bfloat16").cast().valueTypes("short").pointerTypes("ShortPointer", "ShortBuffer",
                                        "short[]"));

        infoMap.put(new Info("__CUDACC__", "MAX_UINT", "HAVE_MKLDNN").define(false))
               .put(new Info("__JAVACPP_HACK__", "LIBND4J_ALL_OPS").define(true))
               .put(new Info("std::initializer_list", "cnpy::NpyArray", "nd4j::NDArray::applyLambda", "nd4j::NDArray::applyPairwiseLambda",
                             "nd4j::graph::FlatResult", "nd4j::graph::FlatVariable").skip())
               .put(new Info("std::string").annotations("@StdString").valueTypes("BytePointer", "String")
                                           .pointerTypes("@Cast({\"char*\", \"std::string*\"}) BytePointer"))
               .put(new Info("std::pair<int,int>").pointerTypes("IntIntPair").define())
               .put(new Info("std::vector<std::vector<int> >").pointerTypes("IntVectorVector").define())
               .put(new Info("std::vector<std::vector<Nd4jLong> >").pointerTypes("LongVectorVector").define())
               .put(new Info("std::vector<nd4j::NDArray*>").pointerTypes("NDArrayVector").define())
               .put(new Info("nd4j::graph::ResultWrapper").base("org.nd4j.nativeblas.ResultWrapperAbstraction").define())
               .put(new Info("bool").cast().valueTypes("boolean").pointerTypes("BooleanPointer", "boolean[]"))
               .put(new Info("nd4j::IndicesList").purify());

        /*
        String classTemplates[] = {
                "nd4j::NDArray",
                "nd4j::NDArrayList",
                "nd4j::ResultSet",
                "nd4j::OpArgsHolder",
                "nd4j::graph::GraphState",
                "nd4j::graph::Variable",
                "nd4j::graph::VariablesSet",
                "nd4j::graph::Stash",
                "nd4j::graph::VariableSpace",
                "nd4j::graph::Context",
                "nd4j::graph::ContextPrototype",
                "nd4j::ops::DeclarableOp",
                "nd4j::ops::DeclarableListOp",
                "nd4j::ops::DeclarableReductionOp",
                "nd4j::ops::DeclarableCustomOp",
                "nd4j::ops::BooleanOp",
                "nd4j::ops::BroadcastableOp",
                "nd4j::ops::LogicOp"};
        for (String t : classTemplates) {
            String s = t.substring(t.lastIndexOf(':') + 1);
            infoMap.put(new Info(t + "<float>").pointerTypes("Float" + s))
                   .put(new Info(t + "<float16>").pointerTypes("Half" + s))
                   .put(new Info(t + "<double>").pointerTypes("Double" + s));
        }
        */

        // pick up custom operations automatically from CustomOperations.h and headers in libnd4j
        String separator = properties.getProperty("platform.path.separator");
        String[] includePaths = properties.getProperty("platform.includepath").split(separator);
        File file = null;
        for (String path : includePaths) {
            file = new File(path, "ops/declarable/CustomOperations.h");
            if (file.exists()) {
                break;
            }
        }
        ArrayList<File> files = new ArrayList<File>();
        ArrayList<String> opTemplates = new ArrayList<String>();
        files.add(file);
        files.addAll(Arrays.asList(new File(file.getParent(), "headers").listFiles()));
        Collections.sort(files);
        for (File f : files) {
            try (Scanner scanner = new Scanner(f, "UTF-8")) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("DECLARE_")) {
                        try {
                            int start = line.indexOf('(') + 1;
                            int end = line.indexOf(',');
                            if (end < start) {
                                end = line.indexOf(')');
                            }
                            String name = line.substring(start, end).trim();
                            opTemplates.add(name);
                        } catch(Exception e) {
                            throw new RuntimeException("Could not parse line from CustomOperations.h and headers: \"" + line + "\"", e);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not parse CustomOperations.h and headers", e);
            }
        }
        logger.info("Ops found in CustomOperations.h and headers: " + opTemplates);
        /*
        String floatOps = "", halfOps = "", doubleOps = "";
        for (String t : opTemplates) {
            String s = "nd4j::ops::" + t;
            infoMap.put(new Info(s + "<float>").pointerTypes("float_" + t))
                   .put(new Info(s + "<float16>").pointerTypes("half_" + t))
                   .put(new Info(s + "<double>").pointerTypes("double_" + t));
            floatOps  += "\n        float_" + t + ".class,";
            halfOps   += "\n        half_" + t + ".class,";
            doubleOps += "\n        double_" + t + ".class,";

        }
        infoMap.put(new Info().javaText("\n"
                                      + "    Class[] floatOps = {" + floatOps + "};" + "\n"
                                      + "    Class[] halfOps = {" + halfOps + "};" + "\n"
                                      + "    Class[] doubleOps = {" + doubleOps + "};"));
        */
        infoMap.put(new Info("nd4j::ops::OpRegistrator::updateMSVC").skip());
    }
}
