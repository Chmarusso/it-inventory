import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { InventoryApiService } from '../inventory-api.service';
import { BrandComboboxComponent } from '../shared/brand-combobox.component';
import { ApiError, CreateEquipment, displayEnum, equipmentTypes } from '../models';

/**
 * Registration form hosted in a native <dialog>.
 *
 * `showModal()` is what makes this accessible: the browser supplies the focus trap, the
 * backdrop, Escape-to-dismiss, the top layer, and `inert` on everything behind it. Hand-rolling
 * those with a div and `role="dialog"` is where custom modals usually go wrong.
 */
@Component({
  selector: 'app-equipment-form-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, BrandComboboxComponent],
  template: `
    <!-- The click handler only implements click-outside-to-dismiss on the backdrop. Its
         keyboard equivalent is Escape, which <dialog> handles natively and surfaces here as
         the (cancel) event, so no extra key handler is warranted. -->
    <!-- eslint-disable-next-line @angular-eslint/template/click-events-have-key-events, @angular-eslint/template/interactive-supports-focus -->
    <dialog #dialog class="modal" aria-labelledby="equipment-dialog-title" (close)="onClose()" (cancel)="onClose()" (click)="onBackdropClick($event)">
      <form #equipmentForm="ngForm" class="modal-panel" (ngSubmit)="submit()" novalidate>
        <header class="modal-header">
          <div>
            <p class="eyebrow">Equipment</p>
            <h3 id="equipment-dialog-title">Register equipment</h3>
          </div>
          <button type="button" class="icon-button" aria-label="Close dialog" (click)="close()">
            <span aria-hidden="true">✕</span>
          </button>
        </header>

        <div class="modal-body">
          <!-- Live region stays mounted so swapped-in text is announced; an @if around the
               whole region would announce nothing on first error. -->
          <div role="alert" aria-live="assertive">
            @if (formError()) { <p class="message error">{{ formError() }}</p> }
          </div>

          <div class="form-grid modal-grid">
            <div class="field">
              <label for="equipment-type">Type</label>
              <select id="equipment-type" name="type" [(ngModel)]="draft.type" required autofocus>
                @for (type of types; track type) { <option [value]="type">{{ displayEnum(type) }}</option> }
              </select>
            </div>

            <div class="field">
              <label for="brand">Brand</label>
              <app-brand-combobox
                inputId="brand"
                name="brand"
                [(ngModel)]="draft.brand"
                [options]="brands()"
                [loading]="brandsLoading()"
                [required]="true"
                [invalid]="!!fieldErrors()['brand']"
                [describedBy]="fieldErrors()['brand'] ? 'brand-error' : null"
              />
              @if (fieldErrors()['brand']) { <small id="brand-error" class="field-error">{{ fieldErrors()['brand'] }}</small> }
            </div>

            <div class="field">
              <label for="model">Model</label>
              <input id="model" name="model" [(ngModel)]="draft.model" required maxlength="100" [attr.aria-invalid]="!!fieldErrors()['model']" [attr.aria-describedby]="fieldErrors()['model'] ? 'model-error' : null">
              @if (fieldErrors()['model']) { <small id="model-error" class="field-error">{{ fieldErrors()['model'] }}</small> }
            </div>

            <div class="field">
              <label for="condition">Condition <span class="hint">0–1</span></label>
              <input id="condition" name="conditionScore" type="number" [(ngModel)]="draft.conditionScore" required min="0" max="1" step="0.01" [attr.aria-invalid]="!!fieldErrors()['conditionScore']" [attr.aria-describedby]="fieldErrors()['conditionScore'] ? 'condition-error' : null">
              @if (fieldErrors()['conditionScore']) { <small id="condition-error" class="field-error">{{ fieldErrors()['conditionScore'] }}</small> }
            </div>

            <div class="field">
              <label for="purchase-date">Purchase date</label>
              <input id="purchase-date" name="purchaseDate" type="date" [(ngModel)]="draft.purchaseDate" required [attr.aria-invalid]="!!fieldErrors()['purchaseDate']" [attr.aria-describedby]="fieldErrors()['purchaseDate'] ? 'date-error' : null">
              @if (fieldErrors()['purchaseDate']) { <small id="date-error" class="field-error">{{ fieldErrors()['purchaseDate'] }}</small> }
            </div>
          </div>
        </div>

        <footer class="modal-footer">
          @if (equipmentForm.invalid) { <span class="button-help">Complete all required fields.</span> }
          <button type="button" class="secondary" (click)="close()" [disabled]="saving()">Cancel</button>
          <button class="primary" type="submit" [disabled]="equipmentForm.invalid || saving()">
            @if (saving()) { <span class="spinner" aria-hidden="true"></span> Registering… } @else { Register equipment }
          </button>
        </footer>
      </form>
    </dialog>
  `,
})
export class EquipmentFormDialogComponent {
  private readonly api = inject(InventoryApiService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly created = output<void>();

  readonly types = equipmentTypes;
  readonly displayEnum = displayEnum;

  readonly open = signal(false);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly brands = signal<string[]>([]);
  readonly brandsLoading = signal(false);

  draft: CreateEquipment = emptyDraft();

  /** The element that opened the dialog, so focus can go back where the user left it. */
  private opener: HTMLElement | null = null;

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      if (this.open()) {
        if (!element.open) element.showModal();
        // showModal() alone still lets the page behind scroll on wheel/touch.
        document.body.classList.add('modal-open');
      } else if (element.open) {
        element.close();
      }
    });
  }

  show(opener: HTMLElement): void {
    this.opener = opener;
    this.draft = emptyDraft();
    this.formError.set('');
    this.fieldErrors.set({});
    this.open.set(true);
    this.loadBrands();
  }

  close(): void {
    this.open.set(false);
  }

  /** Runs for both `close` and `cancel` (Escape), keeping component state and DOM in step. */
  onClose(): void {
    this.open.set(false);
    document.body.classList.remove('modal-open');
    this.opener?.focus();
    this.opener = null;
  }

  /**
   * A native dialog's backdrop is part of the dialog element, so a click lands on the dialog
   * itself only when it hit the backdrop. Clicks on the inner panel target the panel instead.
   */
  onBackdropClick(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) this.close();
  }

  private loadBrands(): void {
    this.brandsLoading.set(true);
    this.api.getBrands().subscribe({
      next: (brands) => {
        this.brands.set(brands);
        this.brandsLoading.set(false);
      },
      // A missing brand list degrades the picker to a plain text field rather than blocking
      // registration, so this failure is deliberately silent.
      error: () => {
        this.brands.set([]);
        this.brandsLoading.set(false);
      },
    });
  }

  submit(): void {
    this.saving.set(true);
    this.formError.set('');
    this.fieldErrors.set({});
    this.api.createEquipment(this.draft).subscribe({
      next: () => {
        this.saving.set(false);
        this.created.emit();
        this.close();
      },
      error: (response: HttpErrorResponse) => {
        const error = response.error as ApiError | undefined;
        this.formError.set(error?.message ?? 'Could not register equipment.');
        this.fieldErrors.set(error?.fieldErrors ?? {});
        this.saving.set(false);
      },
    });
  }
}

function emptyDraft(): CreateEquipment {
  return { type: 'MAIN_COMPUTER', brand: '', model: '', conditionScore: null, purchaseDate: '' };
}
