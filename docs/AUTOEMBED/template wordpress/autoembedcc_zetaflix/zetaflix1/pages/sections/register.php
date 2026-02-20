	<header>
		<h1><?php  _z("Sign up, it's free.."); ?></h1>
	</header>
	<?php do_action ('zt_register_form'); ?>
	<?php if( isset($_GET['form']) && $_GET['form'] == 'send') { /* none */ } else { get_template_part('pages/sections/register-form'); } ?>

