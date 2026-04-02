package com.hearopilot.app.domain.model

/**
 * Static registry of all third-party licenses that HearoPilot must acknowledge.
 *
 * Ordered by legal urgency:
 * 1. Gemma – custom Google terms, NOTICE text is contractually required.
 * 2. Parakeet – CC-BY 4.0, attribution is legally required.
 * 3. Sherpa-ONNX – Apache 2.0.
 * 4. llama.cpp – MIT.
 * 5. ONNX Runtime – MIT.
 */
object AppLicenses {

    val ALL: List<LicenseEntry> = listOf(

        LicenseEntry(
            id = "gemma",
            componentName = "Google Gemma 3",
            licenseType = "Gemma Terms of Use",
            shortNotice = "",
            fullText = "Full terms: https://ai.google.dev/gemma/terms",
            primaryUrl = "https://ai.google.dev/gemma/terms",
            secondaryUrl = "https://huggingface.co/google/gemma-3-1b-it",
            expandedByDefault = true
        ),

        LicenseEntry(
            id = "parakeet",
            componentName = "NVIDIA Parakeet TDT",
            licenseType = "Creative Commons Attribution 4.0 (CC BY 4.0)",
            shortNotice = "",
            fullText = "Full license: https://creativecommons.org/licenses/by/4.0/",
            primaryUrl = "https://creativecommons.org/licenses/by/4.0/",
            secondaryUrl = "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3",
            expandedByDefault = true
        ),

        LicenseEntry(
            id = "sherpa_onnx",
            componentName = "Sherpa-ONNX",
            licenseType = "Apache License 2.0",
            shortNotice = "© 2022–2024 k2-fsa / Next-gen Kaldi authors. " +
                    "Licensed under the Apache License, Version 2.0.",
            fullText = """
                Apache License
                Version 2.0, January 2004
                http://www.apache.org/licenses/

                Copyright 2022-2024 k2-fsa / Next-gen Kaldi authors

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                    http://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            """.trimIndent(),
            primaryUrl = "https://www.apache.org/licenses/LICENSE-2.0",
            githubUrl = "https://github.com/k2-fsa/sherpa-onnx"
        ),

        LicenseEntry(
            id = "llama_cpp",
            componentName = "llama.cpp",
            licenseType = "MIT License",
            shortNotice = "© 2023–2024 The ggml authors. Licensed under the MIT License.",
            fullText = """
                MIT License

                Copyright (c) 2023-2024 The ggml authors

                Permission is hereby granted, free of charge, to any person obtaining a copy
                of this software and associated documentation files (the "Software"), to deal
                in the Software without restriction, including without limitation the rights
                to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
                copies of the Software, and to permit persons to whom the Software is
                furnished to do so, subject to the following conditions:

                The above copyright notice and this permission notice shall be included in all
                copies or substantial portions of the Software.

                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
                IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
                FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
                AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
                LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
                OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
                SOFTWARE.
            """.trimIndent(),
            primaryUrl = "https://github.com/ggml-org/llama.cpp/blob/master/LICENSE",
            githubUrl = "https://github.com/ggml-org/llama.cpp"
        ),

        LicenseEntry(
            id = "onnx_runtime",
            componentName = "ONNX Runtime",
            licenseType = "MIT License",
            shortNotice = "© Microsoft Corporation. All rights reserved. " +
                    "Licensed under the MIT License.",
            fullText = """
                MIT License

                Copyright (c) Microsoft Corporation. All rights reserved.

                Permission is hereby granted, free of charge, to any person obtaining a copy
                of this software and associated documentation files (the "Software"), to deal
                in the Software without restriction, including without limitation the rights
                to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
                copies of the Software, and to permit persons to whom the Software is
                furnished to do so, subject to the following conditions:

                The above copyright notice and this permission notice shall be included in all
                copies or substantial portions of the Software.

                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
                IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
                FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
                AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
                LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
                OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
                SOFTWARE.
            """.trimIndent(),
            primaryUrl = "https://github.com/microsoft/onnxruntime/blob/main/LICENSE",
            githubUrl = "https://github.com/onnx/onnx"
        )
    )
}
