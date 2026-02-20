<?php
/**
 * The searchform.php template.
 *
 * Used any time that get_search_form() is called.
 *
 * @link https://developer.wordpress.org/reference/functions/get_search_form/
 *
 * @package fmovie
 *
 */

?>

<div id="search-toggler">
	<i class="fa fa-search"></i>
</div>
<form id="search" action="<?php echo esc_url( home_url( '/' ) ); ?>" method="get" autocomplete="off">
	<input type="text" name="s" placeholder="<?php echo search; ?>" autocomplete="off" required>
	<button type="submit"><span class="sr-only"><?php echo esc_html__( 'submit', 'fmovie' ) ?></span></button>
	<div class="suggestions"></div>
</form>
