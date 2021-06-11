/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1PARSCANTHREADSTATE_INLINE_HPP
#define SHARE_VM_GC_G1_G1PARSCANTHREADSTATE_INLINE_HPP

#include "gc/g1/g1ParScanThreadState.hpp"
#include "gc/g1/g1RemSet.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"

template <class T> inline void G1ParScanThreadState::push_on_queue(T* ref) {
  assert(verify_ref(ref), "sanity");
  _refs->push(ref);
}

bool G1ParScanThreadState::needs_partial_trimming() const {
  return !_refs->overflow_empty() || _refs->size() > _stack_trim_upper_threshold;
}

void G1ParScanThreadState::trim_queue_partially() {
  if (!needs_partial_trimming()) {
    return;
  }

  const Ticks start = Ticks::now();
  trim_queue_to_threshold(_stack_trim_lower_threshold);
  assert(_refs->overflow_empty(), "invariant");
  assert(_refs->size() <= _stack_trim_lower_threshold, "invariant");
  _trim_ticks += Ticks::now() - start;
}

void G1ParScanThreadState::trim_queue() {
  StarTask ref;
  trim_queue_to_threshold(0);
  assert(_refs->overflow_empty(), "invariant");
  assert(_refs->taskqueue_empty(), "invariant");
}

inline Tickspan G1ParScanThreadState::trim_ticks() const {
  return _trim_ticks;
}

inline void G1ParScanThreadState::reset_trim_ticks() {
  _trim_ticks = Tickspan();
}

#endif // SHARE_VM_GC_G1_G1PARSCANTHREADSTATE_INLINE_HPP
