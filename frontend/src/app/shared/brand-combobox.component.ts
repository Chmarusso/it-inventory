import { ChangeDetectionStrategy, Component, ElementRef, computed, forwardRef, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/**
 * Searchable brand picker following the WAI-ARIA editable combobox pattern.
 *
 * Editable rather than a plain select on purpose: registering equipment from a brand that is
 * not yet in stock is a normal case, so free text must remain valid. The list is an aid, not
 * a constraint.
 */
@Component({
  selector: 'app-brand-combobox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => BrandComboboxComponent), multi: true },
  ],
  template: `
    <div class="combobox">
      <input
        #control
        [id]="inputId()"
        type="text"
        role="combobox"
        autocomplete="off"
        [attr.aria-expanded]="open()"
        [attr.aria-controls]="inputId() + '-listbox'"
        [attr.aria-activedescendant]="activeOptionId()"
        [attr.aria-invalid]="invalid() || null"
        [attr.aria-describedby]="describedBy()"
        aria-autocomplete="list"
        [value]="value()"
        [disabled]="disabled()"
        [required]="required()"
        maxlength="100"
        [placeholder]="placeholder()"
        (input)="onInput($event)"
        (focus)="openList()"
        (blur)="onBlur()"
        (keydown)="onKeydown($event)"
      />
      <span class="combobox-icon" aria-hidden="true">▾</span>

      @if (open()) {
        <ul class="combobox-list" [id]="inputId() + '-listbox'" role="listbox" [attr.aria-label]="listLabel()">
          @if (loading()) {
            <li class="combobox-empty" role="presentation">
              <span class="skeleton skeleton-text"></span>
              <span class="skeleton skeleton-text short"></span>
            </li>
          } @else if (filtered().length === 0) {
            <li class="combobox-empty" role="option" [attr.aria-selected]="false" [id]="inputId() + '-empty'">
              No stocked brand matches — "{{ value() }}" will be added as new.
            </li>
          } @else {
            @for (brand of filtered(); track brand; let index = $index) {
              <li
                role="option"
                [id]="inputId() + '-option-' + index"
                [attr.aria-selected]="brand === value()"
                [class.active]="index === activeIndex()"
                (mousedown)="choose(brand, $event)"
                (mousemove)="highlight(index)"
              >{{ brand }}</li>
            }
          }
        </ul>
      }
    </div>
  `,
})
export class BrandComboboxComponent implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly options = input<string[]>([]);
  readonly loading = input(false);
  readonly required = input(false);
  readonly invalid = input(false);
  readonly describedBy = input<string | null>(null);
  readonly listLabel = input('Brands in stock');
  readonly placeholder = input('Search or type a brand');

  private readonly control = viewChild.required<ElementRef<HTMLInputElement>>('control');

  readonly value = signal('');
  readonly open = signal(false);
  readonly disabled = signal(false);
  private readonly requestedIndex = signal(-1);

  // ControlValueAccessor hands these over in registerOnChange/registerOnTouched. The no-op
  // defaults keep the component usable before a form directive binds to it.
  /* eslint-disable @typescript-eslint/no-empty-function */
  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};
  /* eslint-enable @typescript-eslint/no-empty-function */

  /**
   * Options whose name contains the typed text, case-insensitively. Computed rather than
   * recalculated by hand so it also picks up `options` arriving after the brands request lands.
   */
  readonly filtered = computed(() => {
    const needle = this.value().trim().toLowerCase();
    const all = this.options();
    return needle ? all.filter((brand) => brand.toLowerCase().includes(needle)) : all;
  });

  /** Highlight clamped to the current list, so a shrinking list never points past its end. */
  readonly activeIndex = computed(() => Math.min(this.requestedIndex(), this.filtered().length - 1));

  activeOptionId(): string | null {
    const index = this.activeIndex();
    return this.open() && index >= 0 && index < this.filtered().length
      ? `${this.inputId()}-option-${index}`
      : null;
  }

  openList(): void {
    this.open.set(true);
  }

  onInput(event: Event): void {
    const next = (event.target as HTMLInputElement).value;
    this.value.set(next);
    this.onChange(next);
    this.requestedIndex.set(-1);
    this.openList();
  }

  onBlur(): void {
    this.open.set(false);
    this.requestedIndex.set(-1);
    this.onTouched();
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.filtered().length;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!this.open()) this.openList();
        if (count > 0) this.requestedIndex.set((this.activeIndex() + 1) % count);
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (!this.open()) this.openList();
        if (count > 0) this.requestedIndex.set(this.activeIndex() <= 0 ? count - 1 : this.activeIndex() - 1);
        break;
      case 'Enter': {
        const index = this.activeIndex();
        if (this.open() && index >= 0 && index < count) {
          // Only swallow Enter when it is committing a highlighted option, so Enter on a
          // plain typed value still submits the surrounding form.
          event.preventDefault();
          this.commit(this.filtered()[index]);
        }
        break;
      }
      case 'Escape':
        if (this.open()) {
          // Close the list without letting the keypress reach the dialog and close it too.
          event.preventDefault();
          event.stopPropagation();
          this.open.set(false);
          this.requestedIndex.set(-1);
        }
        break;
      case 'Tab':
        this.open.set(false);
        break;
    }
  }

  highlight(index: number): void {
    this.requestedIndex.set(index);
  }

  choose(brand: string, event: MouseEvent): void {
    // mousedown + preventDefault keeps focus on the input, so blur never races the click.
    event.preventDefault();
    this.commit(brand);
  }

  private commit(brand: string): void {
    this.value.set(brand);
    this.onChange(brand);
    this.open.set(false);
    this.requestedIndex.set(-1);
    this.control().nativeElement.focus();
  }

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }
}
