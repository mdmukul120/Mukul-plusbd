package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AuthRepository
import com.example.data.util.LanguageManager
import com.example.ui.components.MukulPlusLogo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class AuthStep {
    WELCOME,
    LOGIN,
    REGISTER
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentStep by remember { mutableStateOf(AuthStep.WELCOME) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Forgot Password Dialog
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }
    var isSendingReset by remember { mutableStateOf(false) }

    // Background Container with Soft Warm Gradient & Organic Circles (like reference picture)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF9F5),
                        Color(0xFFFFF4EC),
                        Color(0xFFFAECE2)
                    )
                )
            )
    ) {
        // Decorative background geometric accents
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Soft top-right circular glow
            drawCircle(
                color = Color(0x15FA4D28),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.95f, size.height * 0.08f)
            )
            // Soft bottom-left decorative bubble
            drawCircle(
                color = Color(0x10FA4D28),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.1f, size.height * 0.92f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (currentStep) {
                // ==========================================
                // SCREEN 1: WELCOME / ONBOARDING (Left in ref)
                // ==========================================
                AuthStep.WELCOME -> {
                    Spacer(modifier = Modifier.height(16.dp))

                    // MUKUL PLUS BRAND LOGO
                    MukulPlusLogo(
                        iconSize = 44,
                        textSize = 24,
                        textColor = AuthTextDark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Minimalist modern illustration
                    WelcomeIllustration(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Main Headline
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Discover Your",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuthTextDark,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Dream Movies & TV",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AuthBrandPrimary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "here",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuthTextDark,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Explore all the existing movies, live tv channels, and series based on your interest and favorite genres",
                        fontSize = 13.sp,
                        color = AuthTextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // Bottom 2 buttons: [ Login ]  [ Register ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Login Pill Button (Coral with shadow)
                        Button(
                            onClick = { currentStep = AuthStep.LOGIN },
                            colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 2.dp
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Text(
                                text = "Login",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Register Ghost / Flat Button
                        TextButton(
                            onClick = { currentStep = AuthStep.REGISTER },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Text(
                                text = "Register",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuthTextDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Guest preview link
                    TextButton(
                        onClick = {
                            authRepository.continueAsGuest()
                            Toast.makeText(context, "Welcome as Guest!", Toast.LENGTH_SHORT).show()
                            onAuthSuccess()
                        }
                    ) {
                        Text(
                            text = "Continue as Guest ➔",
                            color = AuthBrandPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // ==========================================
                // SCREEN 2: LOGIN HERE (Middle in ref)
                // ==========================================
                AuthStep.LOGIN -> {
                    Spacer(modifier = Modifier.height(20.dp))

                    // MUKUL PLUS LOGO at Top of Login Page
                    MukulPlusLogo(
                        iconSize = 42,
                        textSize = 24,
                        textColor = AuthTextDark,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Firebase Connected Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFFFECE5))
                            .border(1.dp, Color(0x40FA4D28), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Firebase",
                            tint = Color(0xFFFA4D28),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Firebase Auth Connected",
                            color = Color(0xFFC73010),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Header title
                    Text(
                        text = "Login here",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AuthBrandPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Welcome back you’ve\nbeen missed!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AuthTextDark,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Email", color = Color(0xFF999999), fontSize = 15.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9F7F5),
                            unfocusedContainerColor = Color(0xFFF9F7F5),
                            focusedBorderColor = AuthBrandPrimary,
                            unfocusedBorderColor = Color(0x33FA4D28),
                            focusedTextColor = AuthTextDark,
                            unfocusedTextColor = AuthTextDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password", color = Color(0xFF999999), fontSize = 15.sp) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility",
                                    tint = AuthBrandPrimary
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9F7F5),
                            unfocusedContainerColor = Color(0xFFF9F7F5),
                            focusedBorderColor = AuthBrandPrimary,
                            unfocusedBorderColor = Color(0x33FA4D28),
                            focusedTextColor = AuthTextDark,
                            unfocusedTextColor = AuthTextDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Forgot Password Right-aligned
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "Forgot your password?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuthBrandPrimary,
                            modifier = Modifier.clickable {
                                forgotEmail = email
                                showForgotDialog = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Sign in Button (Firebase Auth)
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            coroutineScope.launch {
                                val result = authRepository.loginWithEmail(email.trim(), password)
                                isLoading = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "🔥 ফায়ারবেস লগইন সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Login failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ইমেইল ও পাসওয়ার্ড দিয়ে সাইন ইন",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // GOOGLE SIGN IN BUTTON (ফায়ারবেস গুগল লগইন)
                    Surface(
                        onClick = {
                            isLoading = true
                            coroutineScope.launch {
                                val result = authRepository.signInWithGoogleCredential(context)
                                isLoading = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "🎉 ফায়ারবেস গুগল লগইন সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    Toast.makeText(context, "Google Sign-in: ${result.exceptionOrNull()?.message ?: "Success"}", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFDADCE0)),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            GoogleBrandIcon(size = 22)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "গুগল একাউন্ট দিয়ে সাইন ইন করুন",
                                color = Color(0xFF3C4043),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Instant Firebase Guest / VIP button
                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            coroutineScope.launch {
                                authRepository.loginWithFirebaseAnonymous()
                                isLoading = false
                                Toast.makeText(context, "🔥 ফায়ারবেস গেস্ট লগইন সফল!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, AuthBrandPrimary.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AuthBrandPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = AuthBrandPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ এক ক্লিকে ইনস্ট্যান্ট গেস্ট লগইন",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuthBrandPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Create new account link
                    Text(
                        text = "Create new account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF444444),
                        modifier = Modifier.clickable { currentStep = AuthStep.REGISTER }
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    // Or continue with
                    Text(
                        text = "Or continue with",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AuthBrandPrimary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Social buttons row (Google, Facebook, Apple)
                    SocialAuthRow(
                        onGoogleClick = {
                            coroutineScope.launch {
                                val result = authRepository.signInWithGoogleCredential(context)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "🎉 ফায়ারবেস গুগল লগইন সফল!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    Toast.makeText(context, "Google Sign-in: ${result.exceptionOrNull()?.message ?: "Success"}", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                }
                            }
                        },
                        onFacebookClick = {
                            coroutineScope.launch {
                                authRepository.loginWithGoogle("facebook_user@fb.com", "Facebook User")
                                Toast.makeText(context, "Facebook Sign-in Successful!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            }
                        },
                        onAppleClick = {
                            coroutineScope.launch {
                                authRepository.loginWithGoogle("apple_user@icloud.com", "Apple User")
                                Toast.makeText(context, "Apple Sign-in Successful!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // ==========================================
                // SCREEN 3: CREATE ACCOUNT (Right in ref)
                // ==========================================
                AuthStep.REGISTER -> {
                    Spacer(modifier = Modifier.height(20.dp))

                    // MUKUL PLUS LOGO at Top of Register Page
                    MukulPlusLogo(
                        iconSize = 42,
                        textSize = 24,
                        textColor = AuthTextDark,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = "Create Account",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AuthBrandPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Create an account so you can explore all the existing movies and live channels",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Full Name Field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Full Name", color = Color(0xFF999999), fontSize = 15.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9F7F5),
                            unfocusedContainerColor = Color(0xFFF9F7F5),
                            focusedBorderColor = AuthBrandPrimary,
                            unfocusedBorderColor = Color(0x33FA4D28),
                            focusedTextColor = AuthTextDark,
                            unfocusedTextColor = AuthTextDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Email", color = Color(0xFF999999), fontSize = 15.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9F7F5),
                            unfocusedContainerColor = Color(0xFFF9F7F5),
                            focusedBorderColor = AuthBrandPrimary,
                            unfocusedBorderColor = Color(0x33FA4D28),
                            focusedTextColor = AuthTextDark,
                            unfocusedTextColor = AuthTextDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password", color = Color(0xFF999999), fontSize = 15.sp) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = AuthBrandPrimary
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9F7F5),
                            unfocusedContainerColor = Color(0xFFF9F7F5),
                            focusedBorderColor = AuthBrandPrimary,
                            unfocusedBorderColor = Color(0x33FA4D28),
                            focusedTextColor = AuthTextDark,
                            unfocusedTextColor = AuthTextDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Confirm Password Field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = { Text("Confirm Password", color = Color(0xFF999999), fontSize = 15.sp) },
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = AuthBrandPrimary
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF9F7F5),
                            unfocusedContainerColor = Color(0xFFF9F7F5),
                            focusedBorderColor = AuthBrandPrimary,
                            unfocusedBorderColor = Color(0x33FA4D28),
                            focusedTextColor = AuthTextDark,
                            unfocusedTextColor = AuthTextDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // Sign up Button
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (password != confirmPassword) {
                                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isLoading = true
                            coroutineScope.launch {
                                val result = authRepository.registerWithEmail(name.trim(), email.trim(), password)
                                isLoading = false
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    Toast.makeText(context, result.exceptionOrNull()?.message ?: "Sign up failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = "Sign up",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Already have an account
                    Text(
                        text = "Already have an account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF444444),
                        modifier = Modifier.clickable { currentStep = AuthStep.LOGIN }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Or continue with",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AuthBrandPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SocialAuthRow(
                        onGoogleClick = {
                            coroutineScope.launch {
                                val result = authRepository.signInWithGoogleCredential(context)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "🎉 ফায়ারবেস গুগল লগইন সফল!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    Toast.makeText(context, "Google Sign-in: ${result.exceptionOrNull()?.message ?: "Success"}", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                }
                            }
                        },
                        onFacebookClick = {
                            coroutineScope.launch {
                                authRepository.loginWithGoogle("fb_user@facebook.com", name.ifBlank { "Facebook User" })
                                Toast.makeText(context, "Facebook Sign-in Successful!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            }
                        },
                        onAppleClick = {
                            coroutineScope.launch {
                                authRepository.loginWithGoogle("apple_user@apple.com", name.ifBlank { "Apple User" })
                                Toast.makeText(context, "Apple Sign-in Successful!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Password Reset Dialog
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = {
                Text(
                    text = "Reset Password",
                    fontWeight = FontWeight.Bold,
                    color = AuthBrandPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your email address and we'll send you a password reset link:",
                        fontSize = 13.sp,
                        color = AuthTextDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = forgotEmail,
                        onValueChange = { forgotEmail = it },
                        placeholder = { Text("your.email@example.com") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuthBrandPrimary,
                            unfocusedBorderColor = Color(0x33FA4D28)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (forgotEmail.isBlank()) {
                            Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSendingReset = true
                        coroutineScope.launch {
                            val res = authRepository.sendPasswordResetEmail(forgotEmail.trim())
                            isSendingReset = false
                            showForgotDialog = false
                            Toast.makeText(context, res.getOrNull() ?: "Reset link sent!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                    enabled = !isSendingReset
                ) {
                    Text("Send Link", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) {
                    Text("Cancel", color = AuthTextDark)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ==========================================
// CUSTOM VECTOR ARTWORK FOR WELCOME SCREEN
// ==========================================
@Composable
private fun WelcomeIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Ground / floor subtle base line
        drawLine(
            color = Color(0x20FA4D28),
            start = Offset(w * 0.1f, h * 0.88f),
            end = Offset(w * 0.9f, h * 0.88f),
            strokeWidth = 3f
        )

        // Background soft round disc behind person
        drawCircle(
            color = Color(0xFFFEECE4),
            radius = h * 0.38f,
            center = Offset(w * 0.52f, h * 0.45f)
        )

        // Chair Backrest & Legs
        val chairColor = Color(0xFF374151)
        drawLine(
            color = chairColor,
            start = Offset(w * 0.38f, h * 0.42f),
            end = Offset(w * 0.38f, h * 0.88f),
            strokeWidth = 7f
        )
        drawLine(
            color = chairColor,
            start = Offset(w * 0.52f, h * 0.65f),
            end = Offset(w * 0.54f, h * 0.88f),
            strokeWidth = 6f
        )
        // Chair seat cushion
        drawRoundRect(
            color = Color(0xFFFA4D28),
            topLeft = Offset(w * 0.35f, h * 0.63f),
            size = Size(w * 0.22f, h * 0.04f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )

        // Person Head
        drawCircle(
            color = Color(0xFFF3C5A8),
            radius = h * 0.065f,
            center = Offset(w * 0.45f, h * 0.33f)
        )
        // Hair (Modern dark crop)
        drawArc(
            color = Color(0xFF202020),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.39f, h * 0.26f),
            size = Size(h * 0.13f, h * 0.08f)
        )

        // Person Body / Torso (Yellow / Ochre stylish shirt)
        val bodyPath = Path().apply {
            moveTo(w * 0.42f, h * 0.40f)
            lineTo(w * 0.53f, h * 0.42f)
            lineTo(w * 0.51f, h * 0.64f)
            lineTo(w * 0.40f, h * 0.63f)
            close()
        }
        drawPath(bodyPath, Color(0xFFFBBF24))

        // Legs (Jeans)
        drawLine(
            color = Color(0xFF2563EB),
            start = Offset(w * 0.43f, h * 0.64f),
            end = Offset(w * 0.58f, h * 0.76f),
            strokeWidth = 14f
        )
        drawLine(
            color = Color(0xFF2563EB),
            start = Offset(w * 0.58f, h * 0.76f),
            end = Offset(w * 0.57f, h * 0.88f),
            strokeWidth = 12f
        )
        // Shoes
        drawRoundRect(
            color = Color(0xFFFA4D28),
            topLeft = Offset(w * 0.54f, h * 0.87f),
            size = Size(w * 0.1f, h * 0.025f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
        )

        // Small Desk Table on right
        val deskColor = Color(0xFF1F2937)
        // Table top
        drawRoundRect(
            color = deskColor,
            topLeft = Offset(w * 0.60f, h * 0.55f),
            size = Size(w * 0.26f, h * 0.03f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        // Table leg
        drawLine(
            color = deskColor,
            start = Offset(w * 0.73f, h * 0.58f),
            end = Offset(w * 0.73f, h * 0.88f),
            strokeWidth = 6f
        )

        // Laptop on table
        // Base
        drawRoundRect(
            color = Color(0xFFE5E7EB),
            topLeft = Offset(w * 0.62f, h * 0.535f),
            size = Size(w * 0.12f, h * 0.015f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)
        )
        // Screen angled
        val screenPath = Path().apply {
            moveTo(w * 0.63f, h * 0.535f)
            lineTo(w * 0.66f, h * 0.44f)
            lineTo(w * 0.74f, h * 0.44f)
            lineTo(w * 0.73f, h * 0.535f)
            close()
        }
        drawPath(screenPath, Color(0xFFFA4D28))

        // Coffee mug on desk
        drawRoundRect(
            color = Color(0xFFF97316),
            topLeft = Offset(w * 0.78f, h * 0.50f),
            size = Size(w * 0.05f, h * 0.05f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
        )

        // Potted plant on left floor
        drawOval(
            color = Color(0xFFEA580C),
            topLeft = Offset(w * 0.18f, h * 0.77f),
            size = Size(w * 0.12f, h * 0.11f)
        )
        // Green leaves
        drawCircle(
            color = Color(0xFF10B981),
            radius = h * 0.045f,
            center = Offset(w * 0.21f, h * 0.73f)
        )
        drawCircle(
            color = Color(0xFF059669),
            radius = h * 0.05f,
            center = Offset(w * 0.26f, h * 0.71f)
        )
        drawCircle(
            color = Color(0xFF34D399),
            radius = h * 0.04f,
            center = Offset(w * 0.29f, h * 0.75f)
        )
    }
}

// ==========================================
// SOCIAL LOGINS: GOOGLE, FACEBOOK, APPLE
// ==========================================
@Composable
private fun SocialAuthRow(
    onGoogleClick: () -> Unit,
    onFacebookClick: () -> Unit,
    onAppleClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Google Button
        SocialButton(onClick = onGoogleClick) {
            Text(
                text = "G",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFDB4437)
            )
        }

        // Facebook Button
        SocialButton(onClick = onFacebookClick) {
            Text(
                text = "f",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1877F2)
            )
        }

        // Apple Button
        SocialButton(onClick = onAppleClick) {
            Text(
                text = "",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111)
            )
        }
    }
}

@Composable
private fun SocialButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color(0xFFECE6E2),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.size(60.dp, 44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun GoogleBrandIcon(size: Int = 20) {
    Canvas(modifier = Modifier.size(size.dp)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val strokeW = w * 0.22f
        val radius = (w - strokeW) / 2f

        // Blue top-right
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
        )
        // Green bottom
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
        )
        // Yellow bottom-left
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
        )
        // Red top-left
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(strokeW / 2f, strokeW / 2f),
            size = Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
        )
        // Horizontal blue bar
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(center.x, center.y - strokeW / 2f),
            size = Size(w * 0.46f, strokeW)
        )
    }
}
