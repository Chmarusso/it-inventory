import { ChangeDetectorRef, Component, effect, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { InventoryApiService } from '../inventory-api.service';
import { InventoryRefreshService } from '../inventory-refresh.service';
import { EquipmentFormDialogComponent } from './equipment-form-dialog.component';
import { SkeletonRowsComponent } from '../shared/skeleton-rows.component';
import {
  Equipment,
  EquipmentState,
  EquipmentType,
  displayEnum,
  equipmentStates,
  equipmentTypes,
} from '../models';

@Component({
  selector: 'app-inventory',
  imports: [FormsModule, PercentPipe, RouterLink, NgIcon, EquipmentFormDialogComponent, SkeletonRowsComponent],
  templateUrl: './inventory.component.html',
})
export class InventoryComponent {
  private readonly api = inject(InventoryApiService);
  private readonly refresh = inject(InventoryRefreshService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly dialog = viewChild.required(EquipmentFormDialogComponent);

  readonly types = equipmentTypes;
  readonly states = equipmentStates;
  readonly displayEnum = displayEnum;

  /** Kept in sync with the table header so skeleton rows never shift the layout. */
  readonly columnCount = 6;

  equipment: Equipment[] = [];
  stateFilter: EquipmentState | '' = '';
  typeFilter: EquipmentType | '' = '';
  loading = false;
  listError = '';

  constructor() {
    effect(() => {
      this.refresh.version();
      this.load();
    });
  }

  load(): void {
    this.loading = true;
    this.listError = '';
    this.api.getEquipment(this.stateFilter || undefined, this.typeFilter || undefined).subscribe({
      next: (equipment) => { this.equipment = equipment; this.loading = false; this.changeDetector.markForCheck(); },
      error: () => { this.listError = 'Could not load inventory. Try again.'; this.loading = false; this.changeDetector.markForCheck(); },
    });
  }

  openDialog(event: MouseEvent): void {
    this.dialog().show(event.currentTarget as HTMLElement);
  }

  onCreated(): void {
    this.refresh.notify();
  }
}
