/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2011, 2023, Red Hat Inc. All rights reserved.
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
 *
 */

#include "precompiled.hpp"
#include "hugepages.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"

bool HugePages::_is_thp_always_mode = false;
size_t HugePages::_thp_pagesize = 0;

// Given a file that contains a single (integral) number, return that number in (*out) and true;
// in case of an error, return false.
static bool read_number_file(const char* file, size_t* out) {
  FILE* f = ::fopen(file, "r");
  bool rc = false;
  if (f != NULL) {
    uint64_t i = 0;
    if (::fscanf(f, SIZE_FORMAT, out) == 1) {
      rc = true;
    }
    ::fclose(f);
  }
  return rc;
}

void HugePages::initialize() {
  // Scan /sys/kernel/mm/transparent_hugepage/enabled
  // see mm/huge_memory.c
  const char* filename = "/sys/kernel/mm/transparent_hugepage/enabled";
  FILE* f = ::fopen(filename, "r");
  if (f != NULL) {
    char buf[64];
    char* s = fgets(buf, sizeof(buf), f);
    if (s == buf) {
      _is_thp_always_mode = (::strstr(buf, "[always]") != NULL);
    }
    fclose(f);
  }

  // Scan large page size for THP from hpage_pmd_size
  _thp_pagesize = 0;
  if (!read_number_file("/sys/kernel/mm/transparent_hugepage/hpage_pmd_size", &_thp_pagesize)) {
    _thp_pagesize = 2 * M; // default to the most common page size
  }
}
