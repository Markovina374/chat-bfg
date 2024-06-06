import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
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

  getStorage(): any[] {
    const storage: string | null = localStorage.getItem('chats');
    return storage ? JSON.parse(storage) : [];
  }

  setStorage(data: any): void {
    localStorage.setItem('chats', JSON.stringify(data));
  }
}
