/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package gc.cms;

/* @test TestJStatCMS
 * @bug 8300742
 * @comment Graal does not support CMS
 * @requires vm.gc.ConcMarkSweep & !vm.graal.enabled
 * @library /test/lib /
 * @summary Tests that jstat GCCT(sun.gc.collector.2.time) equals the sum of InitMark and Remark times in gc log.
 * @modules java.base/jdk.internal.misc
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run main/othervm/timeout=300 gc.cms.TestJStatCMS
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;
import gc.testlibrary.PerfCounter;
import gc.testlibrary.PerfCounters;
import java.lang.management.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class TestJStatCMS {
    final static int tolerance = 3; // percentae of the tolerance for the differeance between gc log and jstat

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            int MX = Integer.parseInt(args[0]); // pass -Xmx
            test(MX);
            return;
        }

        /* Run an application which cause lots of CMSGC, and print the gc log and jstat log */
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
             "-Xms64m",
             "-Xmx64m",
             "-XX:+UsePerfData",
             "-XX:+UseConcMarkSweepGC",
             "-Xlog:gc",
             "gc.cms.TestJStatCMS",
             "64"    // MaxHeapSize
             );

        /* Analize that jstat CGCT equals GC log. */
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        // Parse CMGC Inital Mark and Remark logs from the -Xlog:gc log.
        //  [0.281s][info][gc] GC(2) Pause Initial Mark 33M->33M(61M) 1.562ms
        //  [0.695s][info][gc] GC(2) Pause Remark 44M->44M(62M) 1.712ms
        String regexInitMark = "Pause Initial Mark .+\\) [0-9]+\\.[0-9]+ms";
        String regexReMark = "Pause Remark .+\\) [0-9]+\\.[0-9]+ms";
        Pattern patternInitMark = Pattern.compile(regexInitMark);
        Pattern patternReMark = Pattern.compile(regexReMark);

        // Parse jstat CGC and CGCT which are printed at test().
        //  jstatCGC=6109
        //  jstatCGCT=7555.883219794695ms
        String regexJstatCGC = "jstatCGC=[0-9]+";
        String regexJstatCGCT = "jstatCGCT=[0-9]+\\.[0-9]+ms";
        Pattern patternJstatCGC = Pattern.compile(regexJstatCGC);
        Pattern patternJstatCGCT = Pattern.compile(regexJstatCGCT);

        // Parse invocations and times
        String regexInvocations =  "[0-9]+";
        String regexTimeMS =  "[0-9]+\\.[0-9]+";
        Pattern patternInvocations = Pattern.compile(regexInvocations);
        Pattern patternTimeMS = Pattern.compile(regexTimeMS);

        double cmsGGlogPauseTime = 0.0d;
        long cmsGGlogPauseInvocations = 0L;
        double jstatCGCT = 0.0d;
        long jstatCGC = 0L;

        /* Read std output by line. */
        Iterator<String> lines = output.asLines().iterator();
        while (lines.hasNext()) {
            String line = lines.next();
            // gc:log
            Matcher m1 = patternInitMark.matcher(line);
            Matcher m2 = patternReMark.matcher(line);
            String pauseLog = null;
            if (m1.find()) {
                pauseLog = m1.group();
            }
            else if (m2.find()) {
                 pauseLog = m2.group();
            }
            if (pauseLog != null) {
                Matcher m = patternTimeMS.matcher(pauseLog);
                if (m.find()) {
                    String timeLog = m.group();
                    ++cmsGGlogPauseInvocations;
                    cmsGGlogPauseTime += Double.parseDouble(timeLog);
                }
            }

            // jstat
            Matcher m3 = patternJstatCGC.matcher(line);
            Matcher m4 = patternJstatCGCT.matcher(line);
            if (m3.find()) {
                String jstatCGCLog = m3.group();
                if (jstatCGCLog != null) {
                    Matcher m = patternInvocations.matcher(jstatCGCLog);
                    if (m.find()) {
                        String invocations = m.group();
                        jstatCGC = Long.parseLong(invocations);
                    }
                }
            }
            if (m4.find()) {
                String jstatCGCTLog = m4.group();
                if (jstatCGCTLog != null) {
                    Matcher m = patternTimeMS.matcher(jstatCGCTLog);
                    if (m.find()) {
                        String time = m.group();
                        jstatCGCT = Double.parseDouble(time);
                    }
                }
            }
        }

        /* Check that GC log and jstat are equal */
        // gc:log
        System.out.println("cmsGGlogPauseInvocations=" + cmsGGlogPauseInvocations);
        System.out.println("cmsGGlogPauseTime=" + cmsGGlogPauseTime + "ms");
        // jstat
        System.out.println("jstat CGT=" + jstatCGC);
        System.out.println("jstat CGCT=" + jstatCGCT + "ms");

        double ratio = jstatCGCT / cmsGGlogPauseTime;
        System.out.println("jstat/loggc=" + ratio);
        if (cmsGGlogPauseInvocations != jstatCGC) {
           throw new RuntimeException("jstat CGC " + jstatCGC + " is not equal to GC log " + cmsGGlogPauseInvocations);
        }
        if ( Math.abs(1.0d-ratio) > (tolerance/100.0d) ) {
           throw new RuntimeException("jstat CGCT " + jstatCGCT + " is not equal GC log " + cmsGGlogPauseTime + ", the difference(" + ratio + ") is larger than " + tolerance + "%.");
        }
    }

    private static void test(int MX) throws Exception {
        // Causes lots of CMSGC
        CMSGCALot.consumeHeap(MX);
         // System.gc();

        // Get CMSGC puase time and invocations from PerfData which equals jstat CGCT, and print them to std output.
        long CGCT = PerfCounters.findByName("sun.gc.collector.2.time").longValue();
        long CGC = PerfCounters.findByName("sun.gc.collector.2.invocations").longValue();
        long frequency = PerfCounters.findByName("sun.os.hrt.frequency").longValue();
        System.out.println("frequency:" + frequency);
        System.out.println("jstatCGCT=" + ((double)CGCT / (double)frequency) * 1000 + "ms");
        System.out.println("jstatCGC=" + CGC);
    }
}

class CMSGCALot
{
    private static ArrayList arrayList;
    private static TmpClass[] tmp = new TmpClass[1024];

    static void consumeHeap(int MX) throws Exception {

        Random rand = new Random();
        arrayList = new ArrayList();

        int notFreed = (int)(MX * 0.65);
        // Use 0.65% of -Xmx which is not freed
        for (int i = 0 ; i < (1024 * notFreed) ; i++) { // Allocate 1K * 1024 * (MX*0.65) bytes
            byte[] b = new byte[1024];                  // For instance, if MX=64mx,
            arrayList.add(b);                           // consume 1K * 1024 * (64*0.65) = 41.6MB
        }

        // Use 0.35% of -Xmx which is freed
        for (int i = 0 ; i < 1000 ; i++) { // new 1000 * 100000 * 1KB = 100GB which is freed by GC
            Thread.sleep(10);
            for (int j = 0 ; j < 100000 ; j++) {
                tmp[rand.nextInt(1024)] = new TmpClass(1);  // The alive objects are at most 1KB * 1024 bytes
            }                                               // and the other objects are freed by GC.
        }
    }

    static class TmpClass
    {
        ArrayList arrayList;
        TmpClass(int n) {
            arrayList = new ArrayList();
            for (int i = 0 ; i < n ; ++i) {
                byte[] b = new byte[1024];    // new 1KB
                arrayList.add(b);
            }
        }
    }
}
