import { Routes } from '@angular/router';
import { InventoryComponent } from './inventory/inventory.component';
import { AllocationsComponent } from './allocations/allocations.component';
import { AllocationDetailComponent } from './allocations/allocation-detail.component';
import { AllocationFormComponent } from './allocations/allocation-form.component';
import { EquipmentDetailComponent } from './inventory/equipment-detail.component';

export const routes: Routes = [
  { path: 'inventory', component: InventoryComponent, title: 'Inventory | IT Operations' },
  { path: 'inventory/:id', component: EquipmentDetailComponent, title: 'Device | IT Operations' },
  { path: 'allocations', component: AllocationsComponent, title: 'Allocations | IT Operations' },
  { path: 'allocations/new', component: AllocationFormComponent, title: 'New allocation | IT Operations' },
  { path: 'allocations/:id', component: AllocationDetailComponent, title: 'Allocation | IT Operations' },
  { path: '', pathMatch: 'full', redirectTo: 'inventory' },
  { path: '**', redirectTo: 'inventory' },
];
