import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideIcons } from '@ng-icons/core';
import {
  heroArrowLeft,
  heroArrowPath,
  heroArrowTopRightOnSquare,
  heroArrowUturnLeft,
  heroCalendarDays,
  heroCheck,
  heroClipboardDocumentList,
  heroComputerDesktop,
  heroCube,
  heroEye,
  heroPlus,
  heroTag,
  heroTrash,
  heroXMark,
} from '@ng-icons/heroicons/outline';
import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(),
    provideRouter(routes),
    provideIcons({
      heroArrowLeft,
      heroArrowPath,
      heroArrowTopRightOnSquare,
      heroArrowUturnLeft,
      heroCalendarDays,
      heroCheck,
      heroClipboardDocumentList,
      heroComputerDesktop,
      heroCube,
      heroEye,
      heroPlus,
      heroTag,
      heroTrash,
      heroXMark,
    }),
  ],
}).catch((error: unknown) => console.error(error));
