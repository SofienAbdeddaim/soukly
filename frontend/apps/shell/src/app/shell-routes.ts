import { InjectionToken } from '@angular/core';
import { Routes } from '@angular/router';

/**
 * Builds a fresh copy of the shell routes. The router caches the children of a lazy route once
 * they are loaded, including the error page of a remote that failed: "Réessayer" resets the
 * router configuration with new route objects so the remote is requested again.
 */
export const SHELL_ROUTES = new InjectionToken<() => Routes>('SHELL_ROUTES');
