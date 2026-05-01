package app.folga.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folga.ui.common.CrashShareLink
import org.koin.compose.viewmodel.koinViewModel

// Paleta do redesign: azul royal pra primário (botão Entrar, ícones de
// destaque, link "Esqueci minha senha"), azul Google pra accent do
// botão Google, azul claríssimo pra topo do gradiente. Mantemos como
// constantes locais pra não vazar pro tema global — outras telas
// continuam com `MaterialTheme.colorScheme` padrão.
private val RoyalBlue = Color(0xFF4169E1)
private val GoogleBlue = Color(0xFF4285F4)
private val LightBlue = Color(0xFFF0F8FF)

/**
 * Tela de Login com layout redesenhado pelo cliente: gradiente azul
 * claro → branco, círculos decorativos rotacionados no fundo, header
 * com ícone de relógio + "EasyShift", campos com leading icons e
 * botões grandes (56dp) — Entrar (filled), Criar conta (outlined) e
 * Entrar com Google (outlined com badge azul).
 */
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fundo em gradiente vertical: azul claríssimo no topo virando
        // branco no fim. Mantemos um `Box` separado pro fundo pra que
        // os shapes decorativos fiquem por cima e o conteúdo principal
        // por cima de tudo (z-order natural do Box).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(LightBlue, Color.White),
                    ),
                ),
        )

        // Círculos decorativos no fundo — posições/tamanhos/rotações
        // copiadas do template do cliente pra preservar a identidade
        // visual da nova tela.
        DecorativeShape(
            color = RoyalBlue.copy(alpha = 0.10f),
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopStart)
                .offset(x = 40.dp, y = 80.dp)
                .graphicsLayer(rotationZ = -15f),
        )
        DecorativeShape(
            color = GoogleBlue.copy(alpha = 0.08f),
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 120.dp)
                .graphicsLayer(rotationZ = 10f),
        )
        DecorativeShape(
            color = RoyalBlue.copy(alpha = 0.05f),
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.BottomStart)
                .offset(x = 60.dp, y = (-60).dp)
                .graphicsLayer(rotationZ = 25f),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Header: relógio + "EasyShift" lado a lado. `Schedule` é
            // o ícone de relógio do Material; tinta em azul royal pra
            // amarrar com a paleta da tela.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = RoyalBlue,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "EasyShift",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Cadastre e troque seus dias de trabalho",
                fontSize = 18.sp,
                color = Color(0xFF757575),
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "Ocultar senha"
                            } else {
                                "Mostrar senha"
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // "Esqueci minha senha" — pedido do cliente no novo
            // layout. Por enquanto só visual: clicar abre uma mensagem
            // instruindo o usuário a falar com o admin (fluxo de reset
            // de senha entra como follow-up — Firebase Auth já tem
            // `sendPasswordResetEmail`, mas precisa de UI dedicada
            // pra input de e-mail e estado de envio).
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Esqueci minha senha",
                    color = RoyalBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { viewModel.onForgotPassword() },
                )
            }

            if (state.infoMessage != null) {
                Surface(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    color = Color(0xFF0088FF),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = state.infoMessage!!,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Text(
                            "OK",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.dismissInfo() }
                        )
                    }
                }
            }

            if (state.error != null) {
                Surface(
                    modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = state.error!!,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Text(
                            "OK",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewModel.dismissError() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = viewModel::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
            ) {
                if (state.isLoading) CircularProgressIndicator() else Text("Entrar", color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isLoading,
            ) {
                Text("Criar conta")
            }
            Spacer(Modifier.height(16.dp))
            // "Entrar com Google" usa o fluxo nativo (Credential
            // Manager no Android, GoogleSignIn SDK via Swift bridge
            // no iOS). Sem asset oficial bundleado, usamos o
            // AccountCircle do Material em azul Google como stand-in.
            // Trocar pelo SVG oficial fica como follow-up se o
            // cliente quiser fidelidade visual total.
            OutlinedButton(
                onClick = viewModel::signInWithGoogle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isLoading,
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = GoogleBlue,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(12.dp))
                Text("Entrar com Google")
            }

            // Link "Compartilhar último crash" — só renderiza no
            // Android e só se houver um arquivo de crash gravado pelo
            // handler do `FolgaApplication`. É a saída de emergência
            // pra capturar o stack trace quando o app fecha sozinho
            // (caso do bug recorrente do botão Sair).
            CrashShareLink()
        }
    }
}

@Composable
private fun DecorativeShape(
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Forma abstrata pro fundo: círculo simples preenchido com a cor
    // recebida. `clip(CircleShape)` redundante com `background(...,
    // CircleShape)` mas garante o recorte caso o backend de desenho
    // ignore a shape passada pra background.
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color, CircleShape),
    )
}
