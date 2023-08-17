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

#ifndef OS_LINUX_HUGEPAGES_HPP
#define OS_LINUX_HUGEPAGES_HPP

#include "memory/allocation.hpp"
#include "runtime/os.hpp" // for os::PageSizes
#include "utilities/globalDefinitions.hpp"

class outputStream;

enum THPMode { thp_always, thp_never, thp_madvise };

// 2) for transparent hugepages
class THPSupport {
  bool _initialized;

  // See /sys/kernel/mm/transparent_hugepages/enabled
  THPMode _mode;

  // Contains the THP page size
  size_t _pagesize;

public:

  THPSupport();

  // Queries the OS, fills in object
  void scan_os();

  THPMode mode() const;
  size_t pagesize() const;
  void print_on(outputStream* os);
};

// Umbrella static interface
class HugePages : public AllStatic {

  static THPSupport _thp_support;

public:

  static const THPSupport& thp_info() { return _thp_support; }

  static THPMode thp_mode()                     { return _thp_support.mode(); }
  static bool supports_thp()                    { return thp_mode() == thp_madvise || thp_mode() == thp_always; }
  static size_t thp_pagesize()                  { return _thp_support.pagesize(); }

  static void initialize();
  static void print_on(outputStream* os);
};

#endif // OS_LINUX_HUGEPAGES_HPP
