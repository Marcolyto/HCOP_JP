import assert from 'node:assert/strict';
import test from 'node:test';
import {
  schedulerAppointmentBridge,
  schedulerAppointmentCornerClasses,
  schedulerAppointmentSegments,
  schedulerGridInclusiveRange,
  schedulerGridLayout,
  schedulerGridPosition
} from './care-scheduler-grid.models';

test('folds every supported interval into the legacy compact hour matrix', () => {
  const expected = [
    [5, 4, 3, 96, 24],
    [10, 3, 2, 48, 16],
    [15, 2, 2, 32, 16],
    [20, 3, 1, 24, 8],
    [30, 2, 1, 16, 8]
  ];
  for (const [slotMinutes, columnsPerChair, rowsPerHour, totalSlots, visualRows] of expected) {
    const layout = schedulerGridLayout({ slotMinutes, startTime: '08:00', endTime: '16:00' });
    assert.deepEqual(
      [layout.slotMinutes, layout.columnsPerChair, layout.rowsPerHour, layout.totalSlots, layout.visualRows],
      [slotMinutes, columnsPerChair, rowsPerHour, totalSlots, visualRows]
    );
  }
  assert.equal(schedulerGridLayout({ slotMinutes: 7, startTime: '08:00', endTime: '16:00' }).slotMinutes, 10);
});

test('places slots by visible chair and keeps the header in grid row one', () => {
  const layout = schedulerGridLayout({ slotMinutes: 5, startTime: '08:00', endTime: '09:00' });
  assert.deepEqual(schedulerGridPosition(0, 3, layout, 3), { row: 2, column: 1, subColumn: 0, hourGroup: 0 });
  assert.deepEqual(schedulerGridPosition(3, 3, layout, 3), { row: 2, column: 4, subColumn: 3, hourGroup: 0 });
  assert.deepEqual(schedulerGridPosition(4, 3, layout, 3), { row: 3, column: 1, subColumn: 0, hourGroup: 0 });
  assert.deepEqual(schedulerGridPosition(11, 4, layout, 3), { row: 4, column: 8, subColumn: 3, hourGroup: 0 });
  assert.throws(() => schedulerGridPosition(0, 2, layout, 3), /visible viewport/);
});

test('splits an irregular appointment into contiguous puzzle-piece rectangles', () => {
  const layout = schedulerGridLayout({ slotMinutes: 10, startTime: '08:00', endTime: '10:00' });
  const segments = schedulerAppointmentSegments(1, 6, 1, layout);
  assert.deepEqual(segments, [
    { row: 2, rowEnd: 3, columnStart: 2, columnEnd: 4, slotCount: 2 },
    { row: 3, rowEnd: 4, columnStart: 1, columnEnd: 4, slotCount: 3 },
    { row: 4, rowEnd: 5, columnStart: 1, columnEnd: 2, slotCount: 1 }
  ]);
  assert.deepEqual(schedulerAppointmentCornerClasses(segments, 0), [
    'has-convex-top-left', 'has-convex-top-right', 'has-concave-bottom-left'
  ]);
  assert.deepEqual(schedulerAppointmentCornerClasses(segments, 1), [
    'has-convex-top-left', 'has-convex-bottom-right'
  ]);
  assert.deepEqual(schedulerAppointmentCornerClasses(segments, 2), [
    'has-concave-top-right', 'has-convex-bottom-left', 'has-convex-bottom-right'
  ]);
});

test('merges full equal-width rows and clamps a treatment at the closing boundary', () => {
  const layout = schedulerGridLayout({ slotMinutes: 10, startTime: '08:00', endTime: '09:00' });
  assert.deepEqual(schedulerAppointmentSegments(0, 6, 2, layout, 1), [
    { row: 2, rowEnd: 4, columnStart: 4, columnEnd: 7, slotCount: 6 }
  ]);
  assert.deepEqual(schedulerAppointmentSegments(5, 4, 1, layout), [
    { row: 3, rowEnd: 4, columnStart: 3, columnEnd: 4, slotCount: 1 }
  ]);
});

test('adds a bridge only between touching rows whose fragments do not overlap', () => {
  const layout = schedulerGridLayout({ slotMinutes: 10, startTime: '08:00', endTime: '09:00' });
  const disconnected = schedulerAppointmentSegments(2, 2, 1, layout);
  assert.deepEqual(schedulerAppointmentBridge(disconnected[0], disconnected[1]), {
    row: 3, rowEnd: 4, columnStart: 1, columnEnd: 4
  });
  const overlapping = schedulerAppointmentSegments(1, 5, 1, layout);
  assert.equal(schedulerAppointmentBridge(overlapping[0], overlapping[1]), null);
  assert.equal(schedulerAppointmentBridge(undefined, disconnected[0]), null);
});

test('reports the occupied range through the final inclusive minute', () => {
  const layout = schedulerGridLayout({ slotMinutes: 10, startTime: '08:00', endTime: '10:00' });
  assert.deepEqual(schedulerGridInclusiveRange(3, 9, layout), {
    startSlot: 3,
    endSlotExclusive: 12,
    slotCount: 9,
    startMinutes: 510,
    endMinutesInclusive: 599,
    startLabel: '08:30',
    endLabel: '09:59',
    label: '08:30 a 09:59'
  });
  assert.equal(schedulerGridInclusiveRange(layout.totalSlots, 1, layout), null);
});
