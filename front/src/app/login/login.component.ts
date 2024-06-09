import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { ChatService } from '../services/chat/chat.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  public username: string = '';
  public password: string = '';

  constructor(private chatService: ChatService, private router: Router) { }

  login(): void {
    this.chatService.login({ login: this.username, password: this.password }).subscribe(response => {
      localStorage.setItem('token', JSON.stringify(response.token));
      localStorage.setItem('currentUser', JSON.stringify(response.user));
      this.router.navigate(['/chat']);
    }, error => {
      console.error('Login failed', error);
    });
  }
}
