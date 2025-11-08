import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of, throwError, timer } from 'rxjs';
import { catchError, mergeMap, retry, timeout } from 'rxjs/operators';
import { ApiCallbackConfig } from './config';

@Injectable({ providedIn: 'root' })
export class ApiCallbackService {
  private readonly http = inject(HttpClient);

  executeCallback<T>(eventData: T, config: ApiCallbackConfig<T>): Observable<any> {
    const payload = config.transformPayload ? config.transformPayload(eventData) : eventData;
    const headers = new HttpHeaders(config.headers || {});
    
    const options = {
      headers,
      withCredentials: config.withCredentials || false,
    };

    let request$: Observable<any>;

    switch (config.method) {
      case 'POST':
        request$ = this.http.post(config.url, payload, options);
        break;
      case 'PUT':
        request$ = this.http.put(config.url, payload, options);
        break;
      case 'PATCH':
        request$ = this.http.patch(config.url, payload, options);
        break;
      default:
        return throwError(() => new Error(`Unsupported HTTP method: ${config.method}`));
    }

    if (config.timeout) {
      request$ = request$.pipe(timeout(config.timeout));
    }

    return request$.pipe(
      catchError(error => {
        console.error('API callback failed:', error);
        return throwError(() => error);
      })
    );
  }

  executeCallbackWithRetry<T>(
    eventData: T, 
    config: ApiCallbackConfig<T>, 
    retryConfig?: { enabled: boolean; maxRetries: number; delayMs: number }
  ): Observable<any> {
    if (!retryConfig?.enabled) {
      return this.executeCallback(eventData, config);
    }

    return this.executeCallback(eventData, config).pipe(
      retry({
        count: retryConfig.maxRetries,
        delay: (error, retryCount) => {
          console.warn(`API callback retry attempt ${retryCount}/${retryConfig.maxRetries}:`, error);
          return timer(retryConfig.delayMs);
        }
      }),
      catchError(error => {
        console.error(`API callback failed after ${retryConfig.maxRetries} retries:`, error);
        return of(null); // Don't fail the stream, just log the error
      })
    );
  }
}
