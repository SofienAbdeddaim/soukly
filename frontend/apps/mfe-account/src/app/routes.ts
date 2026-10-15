import { Routes } from '@angular/router';
import { AccountComingSoon } from './coming-soon/account-coming-soon';

/**
 * Routes exposed to the shell as "./routes" (federation.config.mjs), relative to the path the
 * shell mounts them on.
 */
export const routes: Routes = [{ path: '**', component: AccountComingSoon }];
