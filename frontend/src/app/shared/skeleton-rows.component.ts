import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Placeholder table rows shown while a request is in flight.
 *
 * Hosted on a <tbody>, not an <ng-container>: a component needs a real host element, and
 * <ng-container> is only a comment anchor, so Angular throws HierarchyRequestError trying to
 * attach one. A table may legally contain several <tbody> elements, so this is valid markup.
 *
 * Column count is an input rather than a constant so the placeholder matches the real table
 * exactly — a mismatch shows up as a visible column jump the moment data arrives.
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'tbody[appSkeletonRows]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @for (row of rowRange(); track row) {
      <tr class="skeleton-row" aria-hidden="true">
        @for (column of columnRange(); track column) {
          <td><span class="skeleton skeleton-text" [style.width.%]="widthFor(column)"></span></td>
        }
      </tr>
    }
  `,
})
export class SkeletonRowsComponent {
  readonly rows = input(5);
  readonly columns = input.required<number>();

  readonly rowRange = computed(() => Array.from({ length: this.rows() }, (_, index) => index));
  readonly columnRange = computed(() => Array.from({ length: this.columns() }, (_, index) => index));

  /** Uneven widths so the placeholder reads as text rather than as a set of identical bars. */
  widthFor(column: number): number {
    return [72, 58, 84, 46, 64][column % 5];
  }
}
