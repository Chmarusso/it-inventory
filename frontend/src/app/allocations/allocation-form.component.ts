import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { InventoryApiService } from '../inventory-api.service';
import { InventoryRefreshService } from '../inventory-refresh.service';
import { BrandComboboxComponent } from '../shared/brand-combobox.component';
import { ApiError, PolicySlot, displayEnum, equipmentTypes } from '../models';

@Component({
  selector: 'app-allocation-form',
  imports: [FormsModule, RouterLink, NgIcon, BrandComboboxComponent],
  templateUrl: './allocation-form.component.html',
})
export class AllocationFormComponent {
  private readonly api = inject(InventoryApiService);
  private readonly refresh = inject(InventoryRefreshService);
  private readonly router = inject(Router);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly types = equipmentTypes;
  readonly displayEnum = displayEnum;

  employeeId = '';
  policy: PolicySlot[] = [this.emptySlot()];
  saving = false;
  formError = '';
  brands: string[] = [];
  brandsLoading = false;

  constructor() {
    this.loadBrands();
  }

  addSlot(): void {
    this.policy.push(this.emptySlot());
  }

  removeSlot(index: number): void {
    if (this.policy.length > 1) this.policy.splice(index, 1);
  }

  create(): void {
    this.saving = true;
    this.formError = '';
    this.api.createAllocation(this.employeeId, this.policy).subscribe({
      next: (allocation) => {
        this.saving = false;
        this.refresh.notify();
        void this.router.navigate(['/allocations', allocation.id]);
      },
      error: (response: HttpErrorResponse) => {
        const error = response.error as ApiError | undefined;
        this.formError = error?.message ?? 'Could not create allocation request.';
        this.saving = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  private loadBrands(): void {
    this.brandsLoading = true;
    this.api.getBrands().subscribe({
      next: (brands) => {
        this.brands = brands;
        this.brandsLoading = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.brands = [];
        this.brandsLoading = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  private emptySlot(): PolicySlot {
    return { type: 'MAIN_COMPUTER', minimumCondition: null, preferredBrand: '', preferRecent: true };
  }
}
