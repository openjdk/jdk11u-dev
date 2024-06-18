/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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
 */

 /*
  * @test
  * @bug 8253700
  * @summary spurious "extends Throwable" at end of method declaration
  * throws section.  Make sure that the link is below a Throws heading.
  * @library /tools/lib ../lib
  * @modules jdk.javadoc/jdk.javadoc.internal.tool
  * @build JavadocTester toolbox.ToolBox
  * @run main TestThrows
  */

 import java.io.IOException;
 import java.nio.file.Path;

 import toolbox.ToolBox;

 public class TestThrows extends JavadocTester {

     public static void main(String... args) throws Exception {
         TestThrows tester = new TestThrows();
         tester.runTests(m -> new Object[] { Path.of(m.getName()) });
     }

     private final ToolBox tb = new ToolBox();

     @Test
     public void testThrowsWithBound(Path base) throws IOException {
         Path src = base.resolve("src");
         tb.writeJavaFiles(src,
                String.join(System.lineSeparator(),
                     "/**",
                     " * This is interface C.",
                     " */",
                     "public interface C {",
                     "    /**",
                     "     * Method m.",
                     "     * @param <T> the throwable",
                     "     * @throws T if a specific error occurs",
                     "     * @throws Exception if an exception occurs",
                     "     */",
                     "    <T extends Throwable> void m() throws T, Exception;",
                     "}"
                     ));

         javadoc("-d", base.resolve("out").toString(),
                 src.resolve("C.java").toString());
         checkExit(Exit.OK);

         checkOutput("C.html", true,
                     "<pre class=\"methodSignature\">&lt;T extends java.lang.Throwable&gt;&nbsp;void&nbsp;m()",
                     "                                throws T,",
                     "                                       java.lang.Exception</pre>",
                     "<dl>",
                     "<dt><span class=\"paramLabel\">Type Parameters:</span></dt>",
                     "<dd><code>T</code> - the throwable</dd>",
                     "<dt><span class=\"throwsLabel\">Throws:</span></dt>",
                     "<dd><code>T</code> - if a specific error occurs</dd>",
                     "<dd><code>java.lang.Exception</code> - if an exception occurs</dd>",
                     "<dd><code>T extends java.lang.Throwable</code></dd>",
                     "</dl>"
                     );
     }
}
