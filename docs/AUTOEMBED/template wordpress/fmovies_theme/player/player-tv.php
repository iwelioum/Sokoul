<?php
/**
 * tv player
 *
 * @package fmovie
 */	

if (isset($_GET['player_tv'])) {
$post_id = $_GET['player_tv'] ?? null;
$season = $_GET['s'] ?? null;
$episode = $_GET['e'] ?? null;
$sv = $_GET['sv'] ?? null;

if ($sv == "premium") {
$tmdb = get_post_meta($post_id, 'imdb_id', true);
} else  {
$tmdb = get_post_meta($post_id, 'id', true);
}

$string = 'emarfi';
$film = strrev ( $string );
?>
<!doctype html>
<html>
	<head>
		<meta charset="<?php bloginfo( 'charset' ); ?>" />
		<meta name="robots" content="noindex,nofollow">
		<style>
			
			body, html{
			background:transparent url(<?php echo esc_url( 'https://i.imgur.com/N0sA28A.gif' ); ?>) no-repeat fixed center;
			position: fixed;
			top: 0px;
			left: 0px;
			width: 100%;
			height: 100%;
			z-index: 200;
			}
			
			#player{
			position: fixed;
			top: 0px;
			left: 0px;
			width: 100%;
			height: 100%;
			z-index: 100;
			}
		</style>
	</head>
	<body>
		<<?php echo $film; ?> id="player" src="./getPlayTV.php?id=<?php echo $tmdb; ?>&s=<?php echo $season; ?>&e=<?php echo $episode; ?>&sv=<?php echo $sv; ?>&playtv=true" scrolling="no" frameborder="0" webkitallowfullscreen="" mozallowfullscreen="" allowfullscreen></<?php echo $film; ?>>
	</body>
</html>
<?php
		} else {
		echo "Missing video_id";
	}
?>