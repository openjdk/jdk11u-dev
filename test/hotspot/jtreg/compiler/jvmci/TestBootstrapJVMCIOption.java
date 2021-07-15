/*
 * Copyright (c) 2021, Red Hat, Inc. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

/*
 * @test TestBootstrapJVMCIOption
 * @bug 8226533
 * @summary Ensure that -XX:+BootstrapJVMCI does not trigger an assertion
 * @requires vm.jvmci
 * @library /test/lib
 * @run driver TestBootstrapJVMCIOption
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestBootstrapJVMCIOption {
    public static void main(String[] args) throws Exception {
        final ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+EnableJVMCI",
                "-XX:+UseJVMCICompiler",
                "-XX:+BootstrapJVMCI",
                "-XX:+PrintBootstrap",
                "--version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Bootstrapping JVMCI");
        output.shouldContain("methods)");
        output.shouldHaveExitValue(0);
    }
}
