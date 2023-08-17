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

#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

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

THPSupport::THPSupport() :
    _initialized(false), _mode(thp_never), _pagesize(SIZE_MAX) {}


THPMode THPSupport::mode() const {
  assert(_initialized, "Not initialized");
  return _mode;
}

size_t THPSupport::pagesize() const {
  assert(_initialized, "Not initialized");
  return _pagesize;
}

void THPSupport::scan_os() {
  // Scan /sys/kernel/mm/transparent_hugepage/enabled
  // see mm/huge_memory.c
  _mode = thp_never;
  const char* filename = "/sys/kernel/mm/transparent_hugepage/enabled";
  FILE* f = ::fopen(filename, "r");
  if (f != NULL) {
    char buf[64];
    char* s = fgets(buf, sizeof(buf), f);
    assert(s == buf, "Should have worked");
    if (::strstr(buf, "[madvise]") != NULL) {
      _mode = thp_madvise;
    } else if (::strstr(buf, "[always]") != NULL) {
      _mode = thp_always;
    } else {
      assert(::strstr(buf, "[never]") != NULL, "Weird content of %s: %s", filename, buf);
    }
    fclose(f);
  }

  // Scan large page size for THP from hpage_pmd_size
  _pagesize = 0;
  if (read_number_file("/sys/kernel/mm/transparent_hugepage/hpage_pmd_size", &_pagesize)) {
    assert(_pagesize > 0, "Expected");
  }
  _initialized = true;

  LogTarget(Info, pagesize) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    print_on(&ls);
  }
}

void THPSupport::print_on(outputStream* os) {
  if (_initialized) {
    os->print_cr("Transparent hugepage (THP) support:");
    os->print_cr("  THP mode: %s",
        (_mode == thp_always ? "always" : (_mode == thp_never ? "never" : "madvise")));
    os->print_cr("  THP pagesize: " SIZE_FORMAT, _pagesize);
  } else {
    os->print_cr("  unknown.");
  }
}

THPSupport HugePages::_thp_support;

void HugePages::initialize() {
  _thp_support.scan_os();
}

void HugePages::print_on(outputStream* os) {
  _thp_support.print_on(os);
}
