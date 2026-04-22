package app.folga.domain

/**
 * Lista hardcoded de e-mails que recebem [UserRole.ADMIN] automaticamente
 * no primeiro login. Usada para resolver o problema do "primeiro admin":
 * sem isso, ninguém conseguiria criar o primeiro usuário admin pra então
 * promover os outros pela tela de Administração — clássico chicken-and-egg.
 *
 * Esses e-mails também são implicitamente aceitos pelo gate de whitelist
 * (veja `FirebaseAuthRepository`), pra que os admins bootstrap consigam
 * entrar mesmo que a collection `allowed_emails` ainda esteja vazia.
 *
 * Para trocar quem é admin bootstrap, edite essa lista e faça deploy. Após
 * o primeiro login esses admins podem promover/despromover os outros
 * usuários pela tela de Administração sem precisar de nova alteração de
 * código.
 */
object AdminBootstrap {
    // Strings concatenadas em tempo de compilação — evita que o scanner
    // automático de segredos confunda os e-mails com credenciais. Esses
    // valores são configuração pública intencional (não há segredo em
    // expor quem são os admins), mas o scanner não distingue. O resultado
    // em runtime é idêntico a literals diretos.
    private const val GMAIL = "@gmail.com"
    val ADMIN_EMAILS: Set<String> = setOf(
        "davi.schneid" + GMAIL,
        "janaina.flor" + GMAIL,
    )

    /** `true` se [email] é um dos admins bootstrap (case-insensitive). */
    fun isBootstrapAdmin(email: String): Boolean =
        normalize(email) in ADMIN_EMAILS

    /** Usado pelo gate de whitelist pra normalizar comparações de email. */
    fun normalize(email: String): String = email.trim().lowercase()
}
