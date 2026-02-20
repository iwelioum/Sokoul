        <form method="post" id="zetaflix_login_user">
            <fieldset class="user"><input type="text" name="log" placeholder="<?php _z('Username'); ?>"></fieldset>
            <fieldset class="password"><input type="password" name="pwd" placeholder="<?php _z('Password'); ?>"></fieldset>
			<fieldset class="xtras">
            <label><input name="rmb" type="checkbox" id="rememberme" value="forever" checked> <?php _z('Remember Me'); ?></label>
			<a class="pteks" href="<?php echo $lostpassword;?>">Lost Password?</a>
			</fieldset>
            <fieldset class="submit"><input id="zetaflix_login_btn" data-btntext="<?php _z('Log in'); ?>" type="submit" value="<?php _z('Log in'); ?>"></fieldset>
			<fieldset class="register">
            <a class="register" href="<?php echo $register; ?>"><?php _z('Create New Account'); ?></a>
            <input type="hidden" name="red" value="<?php echo $redirect; ?>">
            <input type="hidden" name="action" value="zetaflix_login">
			</fieldset>
        </form>
