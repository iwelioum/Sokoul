<form method="POST" id="zetaflix_sign_up" class="register_form">
	<fieldset>
		<label for="user_name"><?php _z('Username'); ?></label>
		<input type="text" placeholder="JohnDoe" id="username" name="username" value="<?php echo isset($_POST['username']) ? $_POST['username'] : false; ?>" required />
	</fieldset>
	<fieldset>
		<label for="email"><?php _z('E-mail address'); ?></label>
		<input type="text" placeholder="johndoe@email.com" id="email" name="email" value="<?php echo isset($_POST['email']) ? $_POST['email'] : false; ?>" required />
	</fieldset>
	<fieldset>
		<label for="spassword"><?php _z('Password'); ?></label>
		<input type="password" id="spassword" name="spassword" required />
		<div class="passwordbox"><div id="passwordStrengthDiv" class="is0"></div></div>
	</fieldset>
	<fieldset class="min fix">
		<label for="zt_name"><?php _z('Name'); ?></label>
		<input type="text" placeholder="John" id="firstname" name="firstname" value="<?php echo isset($_POST['firstname']) ? $_POST['firstname'] : false; ?>" required />
	</fieldset>
	<fieldset class="min">
		<label for="zt_last_name"><?php _z('Last name'); ?></label>
		<input type="text" placeholder="Doe" id="lastname" name="lastname" value="<?php echo isset($_POST['lastname']) ? $_POST['lastname'] : false; ?>" required />
	</fieldset>
    <div id="jsonresponse"></div>
	<fieldset>
		<input name="adduser" type="submit" id="zetaflix_signup_btn" class="submit button" data-btntext="<?php _z('Sign up'); ?>" value="<?php _z('Sign up'); ?>" />
		<span><?php _z('Do you already have an account?'); ?> <a href="<?php echo zeta_compose_pagelink('pageaccount'); ?>"><?php _z('Login here'); ?></a></span>
	</fieldset>
	<div id="zetaflix-reCAPTCHA-response"></div>
	<input name="action" type="hidden" id="action" value="zetaflix_register"/>
</form>
