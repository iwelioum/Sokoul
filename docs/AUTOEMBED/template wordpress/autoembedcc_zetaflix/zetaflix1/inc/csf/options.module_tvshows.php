<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('TV Shows'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'tvtitle',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('TV Shows'),
                'subtitle'    => __z('Add title to show')
            ),
            array(
                'id'    => 'tvshowsmodcontrol',
                'type'  => 'checkbox',
                'title' => __z('Module Control'),
                'subtitle'  => __z('Check to enable option.'),
                'options' => array(
                    'slider' => __z('Activate Module'),
                ),
                'default'=> array('slider')
            ),
            array(
                'id'    => 'tvshowsmdisplay',
                'type'  => 'radio',
                'title' => __z('Display As'),
                'subtitle' => __z('Select style of this module'),
                'options' => array(
                    'slider' => __z('Slider'),
                    'grid'  => __z('Grid')
                ),
                'default' => 'slider'
            ),			
            array(
                'id'      => 'tvshowsitems',
                'type'    => 'text',
                'title'   => __z('Slide Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('tvshowsmdisplay','==','slider'),
            ),
            array(
                'id'      => 'tvshowsgitems',
                'type'    => 'text',
                'title'   => __z('Grid Items'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                ),
                'dependency' => array('tvshowsmdisplay','==','grid'),
			),
            array(
                'id'      => 'tvmodorderby',
                'type'    => 'select',
                'title'   => __z('Order by'),
                'subtitle' => __z('Order items for this module'),
                'default' => 'date',
                'options' => array(
                    'date'     => __z('Post date'),
                    'title'    => __z('Post title'),
                    'modified' => __z('Last modified'),
                    'rand'     => __z('Random entry'),
                )
            ),
            array(
                'id'    => 'tvmodorder',
                'type'  => 'radio',
                'title' => __z('Order'),
                'options' => array(
                    'DESC' => __z('Descending'),
                    'ASC'  => __z('Ascending')
                ),
                'dependency' => array('tvmodorderby','!=','rand'),
                'default' => 'DESC'
            )
        )
    )
);
