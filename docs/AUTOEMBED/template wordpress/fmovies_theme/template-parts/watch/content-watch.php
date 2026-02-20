<?php
/**
 * Displays player
 *
 * @package fmovie
 */
 
$string = 'emarfi';
$film = strrev ( $string );

?>

<div class="play lazyload" loading="lazy" style="background-image: url('<?php echo placeholder; ?>')" data-src="<?php echo Backdrop(); ?>">
	<div class="container">
		<div id="player">
			<div id="play"></div>
			<<?php echo $film; ?> id="<?php echo $film; ?>" loading="lazy" src="about:blank" scrolling="no" frameborder="0" marginwidth="0" marginheight="0" webkitallowfullscreen="true" mozallowfullscreen="true" allowfullscreen></<?php echo $film; ?>>
		</div>
	</div>
</div>