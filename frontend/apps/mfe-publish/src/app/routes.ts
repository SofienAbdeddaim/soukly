import { Routes } from '@angular/router';
import { PublishComingSoon } from './coming-soon/publish-coming-soon';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs), relative to the path the
 * shell mounts them on.
 */
export const routes: Routes = [{ path: '**', component: PublishComingSoon }];
