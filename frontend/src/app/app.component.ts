import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon } from '@ng-icons/core';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, NgIcon],
  template: `
    <header class="app-header">
      <div class="shell header-inner">
        <div>
          <p class="eyebrow">Operations console</p>
          <h1>IT Inventory</h1>
        </div>
        <nav aria-label="Primary navigation">
          <a routerLink="/inventory" routerLinkActive="active"><ng-icon name="heroComputerDesktop" aria-hidden="true" /> Inventory</a>
          <a routerLink="/allocations" routerLinkActive="active"><ng-icon name="heroClipboardDocumentList" aria-hidden="true" /> Allocations</a>
        </nav>
      </div>
    </header>
    <main class="shell"><router-outlet /></main>
    <footer class="app-footer">
      <div class="shell footer-inner">
        <span>IT Inventory</span>
        <a href="https://github.com/Chmarusso/it-inventory" target="_blank" rel="noreferrer">
          View source on GitHub <ng-icon name="heroArrowTopRightOnSquare" aria-hidden="true" />
        </a>
      </div>
    </footer>
  `,
})
export class AppComponent {}
