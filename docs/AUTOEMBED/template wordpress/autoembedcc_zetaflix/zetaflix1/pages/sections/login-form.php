<form id="zetaflix_login_user" method="post">
	<fieldset>
		<label for="log"><?php _z('Username'); ?></label>
		<input type="text" name="log" id="user_login" value="<?php echo isset($_POST['username']) ? $_POST['username'] : false; ?>" required/>
	</fieldset>
	<fieldset>
		<label for="pwd"><?php _z('Password'); ?></label>
		<input type="password" name="pwd" id="user_pass" value="<?php echo isset($_POST['password']) ? $_POST['password'] : false; ?>" required/>
	</fieldset>
	<fieldset>
		<label for="rememberme"><input name="rmb" type="checkbox" id="rememberme" value="forever" checked="checked" /> <?php _z('Stay logged in'); ?></label>
	</fieldset>
    <div id="jsonresponse"></div>
	<fieldset>
		<input type="submit" id="zetaflix_login_btn" data-btntext="<?php _z('Log in'); ?>" class="submit button" value="<?php _z('Sign in'); ?>" />
		<span><?php _z("Don't have an account yet?"); ?> <a href="<?php echo zeta_compose_pagelink('pageaccount') .'?action=signup'; ?>"><?php _z("Sign up here"); ?> </a></span>
		<span><a href="<?php echo esc_url( home_url() ); ?>/wp-login.php?action=lostpassword" target="_blank"><?php _z("I forgot my password"); ?></a></span>
	</fieldset>
	<div id="zetaflix-reCAPTCHA-response"></div>
    <input type="hidden" name="action" value="zetaflix_login">
	<input type="hidden" name="red" value="<?php echo zeta_compose_pagelink('pageaccount'); ?>">
</form>
