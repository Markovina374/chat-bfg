import {Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {ChatService} from '../services/chat/chat.service';
import {Message, MessageResponse, User} from "../models";

@Component({
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.scss']
})
export class ChatComponent implements OnInit {
  public roomId?: string;
  public messageText?: string;
  public messageArray: Map<string, Message[]> = new Map<string, Message[]>();

  public showScreen = false;
  public currentUser?: string;
  public selectedUser?: User;

  public onlineUsers: User[] = [];

  constructor(
    private chatService: ChatService,
    private router: Router
  ) { }

  ngOnInit(): void {
    const token = localStorage.getItem('token');
    if (!token) {
      this.router.navigate(['/login']);
    } else {
      this.initializeChat();
      this.loadOnlineUsers();
      this.subscribeToUserStatusChanges();
      this.currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
      this.showScreen = true;
    }
  }

  private initializeChat(): void {
    this.chatService.getMessage().subscribe((data: { login: string, room: string, message: string, date: string }) => {
      const messages = this.messageArray.get(data.room) || [];
      const isDuplicate = messages.some(message =>
        message.message === data.message &&
        message.login === data.login &&
        message.date === data.date
      );
      if (!isDuplicate) {
        messages.push({
          login: data.login,
          message: data.message,
          date: data.date
        });
        this.messageArray.set(data.room, messages);
      }
    });
  }

  loadOnlineUsers(): void {
    this.chatService.getOnlineUsers().then(users => {
      try {
        const logins = users as string[];
        this.onlineUsers = logins.map(login => ({
          login: login,
          image: `assets/user/${this.getRandomImageNumber()}.png`
        }));
        this.onlineUsers = this.onlineUsers.filter(user => user.login !== this.currentUser);
      } catch (error) {
        console.error('Ошибка при разборе JSON пользователей:', error);
      }
    }).catch(error => {
      console.error('Failed to load online users:', error);
    });
  }

  getRandomImageNumber(): number {
    return Math.floor(Math.random() * 11) + 1;
  }

  subscribeToUserStatusChanges(): void {
    this.chatService.subscribeToUserStatusChanges().subscribe(
      (users) => {
        const logins = users as string[];

        this.onlineUsers = logins.map(login => {
          const existingUser = this.onlineUsers.find(user => user.login === login);
          if (existingUser) {
            return existingUser;
          } else {
            return {
              login: login,
              image: `assets/user/${this.getRandomImageNumber()}.png`
            };
          }
        });

        this.onlineUsers = this.onlineUsers.filter(user => user.login !== this.currentUser);

      },
      (error) => {
        console.error('Failed to subscribe to user status changes:', error);
      }
    );
  }

  selectUserHandler(login: string): void {
    const user = this.onlineUsers.find(user => user.login === login);
    if (user && this.currentUser) {
      this.selectedUser = user;
      this.roomId = this.getRoomId(this.currentUser, user.login);
      this.setMessages(this.roomId);
      this.messageArray.set(this.roomId, this.messageArray.get(this.roomId) || []);
      this.join(this.currentUser, this.roomId);
    }
  }
  setMessages(roomId: string): void {
    this.chatService.getMessages({room: roomId}).then(mess => {
      try {
        const messages: Message[] = mess.map(x => ({ login: x.login,
          message: x.message,
          date: x.date}));
        this.messageArray.set(roomId, messages);
      } catch (error) {
        console.error('Ошибка при разборе JSON пользователей:', error);
      }
    }).catch(error => {
      console.error('Failed to load online users:', error);
    });
  }

  getRoomId(userId: string, selectedUserId: string): string {
    const sortedIds = [userId, selectedUserId].sort((a, b) => a.localeCompare(b));
    return `room-${sortedIds.join('-')}`;
  }

  join(username: string, roomId: string): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.chatService.joinRoom({ user: username, room: roomId, token: token });
    }
  }

  sendMessage(): void {
    if (this.messageText && this.currentUser && this.roomId) {
      const timestamp = new Date().toISOString();
      const messageData: MessageResponse = {
        login: this.currentUser,
        room: this.roomId,
        message: this.messageText,
        date: timestamp
      };

      const token = localStorage.getItem('token');
      if (token) {
        this.chatService.sendMessage({ ...messageData, token: token });

        this.messageText = '';
      }
    }
  }
}
