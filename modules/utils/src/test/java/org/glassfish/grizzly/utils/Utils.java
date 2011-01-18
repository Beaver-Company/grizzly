/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.grizzly.utils;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Class contains set of useful operations commonly used in the framework
 *
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public class Utils {
    public static boolean VERBOSE_TESTS = false;

    public static boolean isDebugVM() {
        boolean debugMode = false;
        List<String> l = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String s : l) {
            if (s.trim().startsWith("-Xrunjdwp:") || s.contains("jdwp")) {
                debugMode = true;
                break;
            }
        }
        return debugMode;
    }

    public static void dumpOut(final Object text) {
        if(VERBOSE_TESTS) {
            System.out.println(text);
        }
    }

    public static void dumpErr(final Object text) {
        if(VERBOSE_TESTS) {
            System.err.println(text);
        }
    }

    public static byte[] copy(final byte[] src) {
        byte[] copy = null;
        if (src != null) {
            copy = new byte[src.length];
            System.arraycopy(src, 0, copy, 0, src.length);
        }
        return copy;
    }

    public static String byteBuffer2String(ByteBuffer bb) {
        return new String(bb.array(), bb.arrayOffset() + bb.position(),
                bb.remaining());
    }    
}
