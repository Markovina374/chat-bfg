import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { ChatService } from '../services/chat/chat.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss']
})
export class RegisterComponent {

  public login: string = '';
  public password: string = '';

  constructor(private chatService: ChatService, private router: Router) { }

  register(): void {
    this.chatService.register({ login: this.login, password: this.password }).subscribe(response => {
      localStorage.setItem('token', JSON.stringify(response.token));
      localStorage.setItem('currentUser', JSON.stringify(response.user));
      this.router.navigate(['/chat']);
    }, error => {
      console.error('Registration failed', error);
    });
  }
}
