import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { WebSocketSubject } from 'rxjs/webSocket';
import { MessageResponse } from "../../models";

@Injectable({
  providedIn: 'root'
})
export class ChatService {
  private socket$: WebSocketSubject<any>;
  private url = 'ws://localhost:8090';

  constructor() {
    this.socket$ = new WebSocketSubject(this.url);
  }

  joinRoom(data: any): void {
    this.socket$.next({ event: 'join', data });
  }

  sendMessage(data: any): void {
    this.socket$.next({ event: 'message', data });
  }

  getMessage(): Observable<{ user: string, room: string, message: string }> {
    return new Observable(observer => {
      this.socket$.subscribe({
        next: (msg) => observer.next(msg),
        error: (err) => observer.error(err),
        complete: () => observer.complete()
      });

      return () => {
        this.socket$.complete();
      };
    });
  }

  login(data: any): Observable<any> {
    const loginSubject = new Subject<any>();
    this.socket$.next({ event: 'auth', data });

    this.socket$.subscribe({
      next: (msg) => {
        console.log(msg)
        if (msg.status === 'authenticated') {
          loginSubject.next(msg);
          loginSubject.complete();
        }
      },
      error: (err) => loginSubject.error(err),
      complete: () => loginSubject.complete()
    });

    return loginSubject.asObservable();
  }

  register(data: any): Observable<any> {
    const registerSubject = new Subject<any>();
    this.socket$.next({ event: 'register', data });

    this.socket$.subscribe({
      next: (msg) => {
        if (msg.event === 'register_response') {
          registerSubject.next(msg.data);
          registerSubject.complete();
        }
      },
      error: (err) => registerSubject.error(err),
      complete: () => registerSubject.complete()
    });

    return registerSubject.asObservable();
  }


  getAllUsers(): Observable<any> {
    const subject = new Subject<any>();

    this.socket$.next({ event: 'getAllUsers' });

    this.socket$.subscribe(
      (msg) => {
        if (msg.event === 'getAllUsers') {
          subject.next(msg.data);
          subject.complete();
        }
      },
      (err) => {
        subject.error(err);
      }
    );

    return subject.asObservable();
  }

  getStorage(): any[] {
    const storage: string | null = localStorage.getItem('chats');
    return storage ? JSON.parse(storage) : [];
  }

  setStorage(data: any): void {
    localStorage.setItem('chats', JSON.stringify(data));
  }
}
