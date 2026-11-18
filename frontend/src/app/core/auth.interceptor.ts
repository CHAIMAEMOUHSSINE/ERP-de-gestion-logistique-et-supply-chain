import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Ajoute le jeton JWT aux appels API et déconnecte l'utilisateur si le jeton est refusé. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isLogin = req.url.endsWith('/auth/login');
  const token = auth.token;
  const request = token && !isLogin ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isLogin) { auth.logout(); }
      return throwError(() => err);
    }),
  );
};
