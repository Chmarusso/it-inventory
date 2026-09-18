import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { PercentPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { InventoryApiService } from '../inventory-api.service';
import { Equipment, displayEnum } from '../models';

@Component({
  selector: 'app-equipment-detail',
  imports: [PercentPipe, RouterLink, NgIcon],
  templateUrl: './equipment-detail.component.html',
})
export class EquipmentDetailComponent {
  private readonly api = inject(InventoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly displayEnum = displayEnum;

  equipment: Equipment | null = null;
  loading = true;
  loadError = '';

  constructor() {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id)) {
      this.loading = false;
      this.loadError = 'This device could not be found.';
      return;
    }
    this.loading = true;
    this.loadError = '';
    this.api.getEquipmentById(id).subscribe({
      next: (equipment) => {
        this.equipment = equipment;
        this.loading = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.loadError = 'Could not load this device.';
        this.loading = false;
        this.changeDetector.markForCheck();
      },
    });
  }
}
